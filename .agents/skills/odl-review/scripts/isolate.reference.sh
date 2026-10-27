#!/bin/bash
# Reference implementation of the "isolated run" capability (SKILL.md, "Capabilities"), with Docker.
# Use it only if the environment instructions do not give another implementation.
#
#   isolate.reference.sh check
#       Checks the properties I1-I6 inside a container with the same settings as the runs.
#       Writes $VERDICT_WORK/env-check-<settings id>.txt. Exit 0 only if all properties pass.
#       Each set of settings (image, limits, mounts) has its own check.
#   isolate.reference.sh run <src-tree> <label> <timeout-sec> '<command in /work/tree>'
#       Refuses to run if the last check did not pass, or used other settings (exit 3), if
#       prepare.py check-tree refuses a tree of trees/ (exit 5), or if a copy fails (exit 4).
#       Copies <src-tree> (without .git and target/) to a new run folder, copies
#       $VERDICT_WORK/extra/<label>/ over it, and runs the command in a new container, in a copy
#       of that folder on a new Docker volume (/scratch/tree; removed after the run).
#       Files in /work/out go to $VERDICT_WORK/out/<label>/. Log: $VERDICT_WORK/logs/<label>.log.
#       Outputs are untrusted: before the copy, entries that are not regular files or folders
#       (symlinks, devices, pipes) are deleted and counted. Appends one line to $VERDICT_WORK/manifest.jsonl.
#
# Settings (environment variables):
#   VERDICT_WORK   required; the work folder
#   VERDICT_IMAGE  container image (default maven:3.9-eclipse-temurin-17); build it only from main
#   VERDICT_CACHE  optional host folder, mounted read-only at /cache (for example a Maven repository)
#   VERDICT_MOUNT  optional "host:container" for one more read-only mount (for example a wheelhouse)
#   VERDICT_SLOTS  parallel containers (default 2); VERDICT_MEM (default 3500m); VERDICT_CPUS (default 2)
set -u
: "${VERDICT_WORK:?set VERDICT_WORK}"
HERE=$(cd "$(dirname "$0")" && pwd -P)
IMAGE=${VERDICT_IMAGE:-maven:3.9-eclipse-temurin-17}
SLOTS=${VERDICT_SLOTS:-2}; MEM=${VERDICT_MEM:-3500m}; CPUS=${VERDICT_CPUS:-2}
W=$VERDICT_WORK; mkdir -p "$W"/{logs,out,runs,extra,slots}

mounts=()
[ -n "${VERDICT_CACHE:-}" ] && mounts+=(-v "$VERDICT_CACHE:/cache:ro")
[ -n "${VERDICT_MOUNT:-}" ] && mounts+=(-v "$VERDICT_MOUNT:ro")
settings="image=$IMAGE mem=$MEM cpus=$CPUS cache=${VERDICT_CACHE:-} mount=${VERDICT_MOUNT:-}"
settings_id=$(python3 -I -c 'import hashlib,sys; print(hashlib.sha256(sys.argv[1].encode()).hexdigest()[:12])' "$settings")

container() {  # container <timeout> <workdir-host> <command> [scratch volume]
  local scratch=()
  [ -n "${4:-}" ] && scratch=(-v "$4":/scratch)
  docker run --rm --network none --read-only --cap-drop ALL --security-opt no-new-privileges \
    --user 1000:1000 --pids-limit 1024 --memory "$MEM" --cpus "$CPUS" --tmpfs /tmp:exec,size=4g ${scratch[@]+"${scratch[@]}"} \
    -e HOME=/tmp -e JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -Djava.io.tmpdir=/tmp" \
    -e MAVEN_OPTS="-Duser.home=/tmp -Djava.awt.headless=true" \
    -v "$2":/work ${mounts[@]+"${mounts[@]}"} -w /work/tree "$IMAGE" timeout "$1" bash -c "$3"
}

if [ "${1:-}" = check ]; then
  RUN=$(mktemp -d "$W/runs/check.XXXXXX"); mkdir -p "$RUN/tree"; echo probe > "$RUN/tree/probe"
  out=$(container 120 "$RUN" '
    [ "$HOME" = /tmp ] && [ ! -e /Users ] && [ ! -e /home/runner ] && ! ls /tmp/.ssh /tmp/.config/gh >/dev/null 2>&1 \
      && [ "$(env | grep -ciE "token|secret|password|aws_|gh_")" = 0 ] && echo "I1=pass" || echo "I1=fail"
    ifs=$(ls /sys/class/net | tr "\n" " "); { [ "$ifs" = "lo " ] && ! getent hosts example.com >/dev/null 2>&1; } && echo "I2=pass" || echo "I2=fail ($ifs)"
    { ! touch /usr/i3 2>/dev/null && { [ ! -d /cache ] || ! touch /cache/i3 2>/dev/null; } && touch /work/tree/w; } && echo "I3=pass" || echo "I3=fail"
    grep -q "^CapEff:[[:space:]]*0*$" /proc/self/status && grep -q "^NoNewPrivs:[[:space:]]*1" /proc/self/status && [ "$(id -u)" != 0 ] && echo "I4=pass" || echo "I4=fail"
    { [ "$(cat /sys/fs/cgroup/pids.max)" != max ] && [ "$(cat /sys/fs/cgroup/memory.max)" != max ]; } && echo "I6=pass" || echo "I6=fail"' 2>/dev/null)
  [ "$(cat "$RUN/tree/probe" 2>/dev/null)" = probe ] || out="$out I3=fail"
  rm -rf "$RUN"; [ ! -e "$RUN" ] && out="$out I5=pass" || out="$out I5=fail"
  CHK=$W/env-check-$settings_id.txt
  echo "$out" | tr ' ' '\n' | grep -E "^I[0-9]=" | sort > "$CHK"
  echo "settings=$settings_id $settings" >> "$CHK"; cat "$CHK"
  grep -q fail "$CHK" && exit 1
  [ "$(grep -c '=pass' "$CHK")" -eq 6 ] || exit 1
  exit 0
fi

[ "${1:-}" = run ] || { sed -n '2,24p' "$0"; exit 2; }
SRC=${2%/}; LABEL=$3; T=$4; CMD=$5
[[ "$LABEL" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "bad label: $LABEL"; exit 2; }
CHK=$W/env-check-$settings_id.txt
if [ ! -f "$CHK" ] || grep -q fail "$CHK" || [ "$(grep -c '=pass' "$CHK")" -ne 6 ]; then
  echo "refused: no passing isolation check for these settings. Run: $0 check"; exit 3
fi
# A tree of prepare.py must still be what it wrote (C6): an agent whose shell was in the tree can add files there (#721).
# A folder in trees/ needs its manifest (#836); other sources, for example an empty folder, have none.
if [ -f "$SRC.manifest" ] || [ "$(basename "$(dirname "$SRC")")" = trees ]; then
  python3 -I "$HERE/prepare.py" check-tree --tree "$SRC" || { echo "refused: the tree changed since prepare.py trees (see above)"; exit 5; }
fi
while :; do
  for i in $(seq 1 "$SLOTS"); do mkdir "$W/slots/$i" 2>/dev/null && { SLOT=$W/slots/$i; break 2; }; done
  sleep 5
done
RUN=$(mktemp -d "$W/runs/$LABEL.XXXXXX")
# The build runs in a copy on a new Docker volume, not on the host bind mount: a host file system
# (for example virtiofs or sshfs of a VM) can break file deletes that tests depend on (#705).
VOL=verdict-$(basename "$RUN" | tr -c 'A-Za-z0-9_.-' '-')
trap 'rm -rf "$RUN"; rmdir "$SLOT" 2>/dev/null; docker volume rm -f "$VOL" >/dev/null 2>&1' EXIT
docker volume create "$VOL" >/dev/null
docker run --rm --network none --cap-drop ALL --cap-add CHOWN -v "$VOL":/scratch "$IMAGE" chown 1000:1000 /scratch
# A run on a part of the tree can pass (for example "true"), so a failed copy stops the run (#836).
rsync -a --exclude .git --exclude 'target/' "$SRC/" "$RUN/tree/" || { echo "refused: the copy of the tree failed"; exit 4; }
if [ -d "$W/extra/$LABEL" ]; then
  rsync -a "$W/extra/$LABEL/" "$RUN/tree/" || { echo "refused: the copy of extra/$LABEL failed"; exit 4; }
fi
mkdir -p "$RUN/out"
rev=$(cat "$SRC.rev" 2>/dev/null || git -C "$SRC" rev-parse HEAD 2>/dev/null || echo none)
start=$(date -u +%Y-%m-%dT%H:%M:%SZ)
rm -rf "$W/out/$LABEL"; mkdir -p "$W/out/$LABEL"
container "$T" "$RUN" "mkdir -p /scratch/tree && cp -a /work/tree/. /scratch/tree && cd /scratch/tree && { $CMD; }" "$VOL" > "$W/logs/$LABEL.log" 2>&1; rc=$?
# Outputs are untrusted (C8): no links or special files, and no agent instruction files (the list
# and the Unicode folding of prepare.py).
counts=$(python3 -I "$HERE/prepare.py" strip-outputs "$RUN/out") && read -r removed removed_instr <<< "$counts"
if [[ "${removed:-}" =~ ^[0-9]+$ && "${removed_instr:-}" =~ ^[0-9]+$ ]]; then
  cp -R "$RUN/out/." "$W/out/$LABEL/"
else
  echo "refused: the outputs could not be cleaned, so they were not copied"; removed=error; removed_instr=error; rc=4
fi
[ "$removed" != 0 ] && echo "warning: removed $removed output entries that are not regular files (symlinks, devices, pipes)"
[ "$removed_instr" != 0 ] && echo "warning: removed $removed_instr agent instruction files or folders from the outputs"
python3 -I -c 'import json,sys; print(json.dumps(dict(zip(["time","label","tree","rev","overlay","settings","rc","removed_links","removed_instructions","command"], sys.argv[1:]))))' \
  "$start" "$LABEL" "$SRC" "$rev" "$([ -d "$W/extra/$LABEL" ] && echo "extra/$LABEL" || echo none)" "$settings_id" "$rc" "$removed" "$removed_instr" "$CMD" >> "$W/manifest.jsonl"
echo "$LABEL rc=$rc"; exit $rc
