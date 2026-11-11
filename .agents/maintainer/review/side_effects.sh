#!/bin/bash
# Side-effect corpus (catalog: Q2-corpus, Q2-tagged). Run it INSIDE the isolated environment:
#
#   bash side_effects.sh <main.jar> <merge.jar> <corpus-dir> <out-dir> [extra CLI options ...]
#
# <corpus-dir> must hold the corpus of MAIN (for example samples/pdf of the main tree, copied into
# the overlay), never the corpus of the tree that runs: a pull request can change its own samples.
# The script refuses a corpus inside the current directory (the run copy of the merge).
# It converts each folder of <corpus-dir>, and the PDFs at its top as the folder "_top", with both jars to json, markdown, html, text, and
# tagged-pdf, and writes:
#   <out-dir>/{main,merge}/...        the outputs
#   <out-dir>/{main,merge}.errors     error lines of each run
#   <out-dir>/summary.txt             the error diff and the list of changed output files
# The output is deterministic (also the tagged PDFs), so each difference comes from the change.
# Run again with options that change the code path (for example --table-method cluster) in
# another out-dir.
set -u
MAIN=$1; MERGE=$2; CORPUS=$3; OUT=$4; shift 4
here=$(pwd -P); corpus=$(cd "$CORPUS" 2>/dev/null && pwd -P) || { echo "no corpus folder: $CORPUS"; exit 2; }
case "$corpus/" in "$here"/*) echo "refused: the corpus is inside the run copy ($corpus); use the corpus of main"; exit 2 ;; esac
rm -rf "$OUT"; mkdir -p "$OUT"
for side in main merge; do
  jar=$MAIN; [ $side = merge ] && jar=$MERGE
  for d in "$corpus"/*/; do
    [ -d "$d" ] || continue  # a corpus without subfolders (#836)
    d=${d%/}; name=${d##*/}
    java -Djava.awt.headless=true -jar "$jar" -f json,markdown,html,text,tagged-pdf "$@" -o "$OUT/$side/$name" "$d" >> "$OUT/$side.log" 2>&1
    echo "$side $name: exit $?" >> "$OUT/$side.log"
  done
  tops=()  # one element per PDF, also for a name with a space (#836)
  while IFS= read -r -d '' p; do tops+=("$p"); done < <(find "$corpus" -maxdepth 1 -type f -name '*.pdf' -print0 | sort -z)
  if [ ${#tops[@]} -gt 0 ]; then  # the PDFs at the top of the corpus, as one more folder
    java -Djava.awt.headless=true -jar "$jar" -f json,markdown,html,text,tagged-pdf "$@" -o "$OUT/$side/_top" "${tops[@]}" >> "$OUT/$side.log" 2>&1
    echo "$side _top: exit $?" >> "$OUT/$side.log"
  fi
  grep -a -E '^Error|Exception during processing file' "$OUT/$side.log" | sed 's/[0-9]\{4\}-[0-9-]* [0-9:.,]*//' > "$OUT/$side.errors"
done
{
  echo "corpus: $(ls "$corpus" | tr '\n' ' ')($(find "$corpus" -type f -name '*.pdf' | wc -l | tr -d ' ') PDFs)"
  echo "== error diff (main -> merge)"; diff "$OUT/main.errors" "$OUT/merge.errors" && echo "(none)"
  echo "== changed outputs"; diff -rq "$OUT/main" "$OUT/merge" | sed "s|$OUT/||g" || true
  echo "== count: $(diff -rq "$OUT/main" "$OUT/merge" | wc -l | tr -d ' ') files differ ($(diff -rq "$OUT/main" "$OUT/merge" | grep -c '_tagged\.pdf') tagged PDFs)"
} > "$OUT/summary.txt"
cat "$OUT/summary.txt"
