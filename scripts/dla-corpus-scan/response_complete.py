#!/usr/bin/env python3
"""Exit 0 only if a DLA response is complete and usable.

A response is usable when the envelope succeeded *and* every page inside it
reports success. The server can return HTTP 200 with SUCCESS:true while an
individual page failed, and caching that would keep serving a partial document
as though it were whole — the retry would never run and the report would count
a truncated document as good.

Used by scan.sh both to promote a fresh download and to decide whether an
existing file can be resumed from.
"""
import json
import sys


def validated_pages(doc):
    """Returns the response's pages, or None if the response is not usable.

    Rejects rather than filters: a malformed page record means the response is
    not the shape we recorded against, and dropping just that entry would let a
    partial document be cached and counted as whole.
    """
    if not isinstance(doc, dict):
        return None
    # A truthy non-boolean (e.g. the string "yes") is not a success flag.
    if doc.get("SUCCESS") is not True:
        return None

    result = doc.get("RESULT")
    if not isinstance(result, list):
        return None

    found = []
    for entry in result:
        records = entry if isinstance(entry, list) else [entry]
        for record in records:
            if not isinstance(record, dict):
                return None
            found.append(record)

    if not found:
        return None
    if any(record.get("MSG") != "success" for record in found):
        return None
    return found


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: response_complete.py <response.json>")
    try:
        with open(sys.argv[1], encoding="utf-8") as handle:
            doc = json.load(handle)
    except (OSError, ValueError):
        return 1
    return 0 if validated_pages(doc) is not None else 1


if __name__ == "__main__":
    sys.exit(main())
