#!/bin/bash
# Download one issue attachment safely. Links in an issue are untrusted input.
#
#   fetch_attachment.sh '<https link>' <output file>
#
# - Only https links to github.com or *.githubusercontent.com. Other hosts print ASK.
# - Redirects are followed one at a time, at most 5, and each new host is checked again.
# - Files larger than 100 MB stop the download. The output is deleted on each failure.
# - You name the output file. Do not use a file name from the issue.
# Exit: 0 downloaded; 2 ASK (give the link to the user); 1 failed.
set -u
link=$1; out=$2
[ -n "$link" ] && [ -n "$out" ] || { sed -n '2,11p' "$0"; exit 2; }
for hop in 0 1 2 3 4 5; do
  case "$link" in https://*) ;; *) echo "ASK: not https: $link"; rm -f "$out"; exit 2 ;; esac
  case "$link" in *"'"*) echo "ASK: quote in link"; rm -f "$out"; exit 2 ;; esac
  host=${link#https://}; host=${host%%[/?#]*}
  case "$host" in
    *[@:\\]*) echo "ASK: $link"; rm -f "$out"; exit 2 ;;
    github.com|*.githubusercontent.com) ;;
    *) echo "ASK: host $host"; rm -f "$out"; exit 2 ;;
  esac
  res=$(curl -g -sS --proto '=https' --max-filesize 100M -o "$out" -w '%{http_code} %{redirect_url}' "$link"); rc=$?
  code=${res%% *}; next=${res#* }
  case "$rc:$code" in
    0:200)
      type=$(file -b "$out")
      case "$type" in *HTML*) echo "FAIL: web page, not a file: $link"; rm -f "$out"; exit 1 ;; esac
      echo "OK: $out ($type)"; exit 0 ;;
    0:301|0:302|0:303|0:307|0:308)
      [ -n "$next" ] && [ "$next" != "$code" ] || { echo "FAIL: redirect without location"; rm -f "$out"; exit 1; }
      link=$next ;;
    63:*) echo "FAIL: larger than 100 MB"; rm -f "$out"; exit 1 ;;
    *) echo "FAIL: http $code, curl exit $rc"; rm -f "$out"; exit 1 ;;
  esac
done
echo "ASK: more than 5 redirects"; rm -f "$out"; exit 2
