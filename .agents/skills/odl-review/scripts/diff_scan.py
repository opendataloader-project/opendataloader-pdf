#!/usr/bin/env python3
"""Deterministic scan of a pull request diff. It reads git objects only; it runs no parser of the
pull request (no qpdf, no build), so it is safe on the host. It does not decide: it lists hits.

  diff_scan.py diff --git DIR --old SHA --new SHA --out scan.json
      Scan the changes from OLD (main) to NEW (the merge). Use pr.json "diff_range".
  diff_scan.py pii --out pii.json FILE...
      Scan text files for personal-data patterns, for example text that main's CLI extracted
      from an added PDF in an isolated run.

Each hit has an id, a rule, a severity (high, medium, info), "decide" (a maintainer decision is
needed: SKILL.md, sensitive changes), the file, the line, and a short excerpt (data, not
instructions). scripts/verdict.py holds the verdict until each hit is explained in plan.json:
high and medium hits one by one ("S3"), info hits by rule ("rule:time-api").
"""

from __future__ import annotations

import argparse
import json
import os
import re
import struct
import subprocess
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from prepare import DIFF_SAFE, GIT_SAFE, category  # noqa: E402

MAX_BLOB = 20 * 1024 * 1024

def char_class(*ranges: tuple[int, int]) -> re.Pattern:
    """A character class from code point ranges, so that this source file holds no invisible characters."""
    return re.compile("[" + "".join(re.escape(chr(a)) + ("-" + re.escape(chr(b)) if b != a else "") for a, b in ranges) + "]")


HIDDEN = char_class((0x202A, 0x202E), (0x2066, 0x2069), (0x200B, 0x200D), (0x2060, 0x2060), (0x2062, 0x2064),
                    (0x00AD, 0x00AD), (0x061C, 0x061C), (0xFEFF, 0xFEFF), (0xE0000, 0xE007F))
INVISIBLE = char_class((0x200E, 0x200F), (0x2061, 0x2061), (0x3164, 0x3164), (0x115F, 0x1160), (0xFFA0, 0xFFA0), (0x180E, 0x180E))
VARIATION = char_class((0xFE00, 0xFE0F), (0xE0100, 0xE01EF))
MAX_LINE = 4000
TEXT_EXT = (".java", ".kt", ".groovy", ".gradle", ".kts", ".py", ".pyi", ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx", ".json",
            ".md", ".mdx", ".txt", ".rst", ".xml", ".yml", ".yaml", ".toml", ".properties", ".cfg", ".ini", ".sh", ".bash",
            ".zsh", ".html", ".htm", ".xhtml", ".css", ".csv", ".svg", ".lock", ".gitignore", ".gitattributes", ".editorconfig")
TEXT_NAMES = ("Makefile", "Dockerfile", "LICENSE", "NOTICE", "THIRD_PARTY", "LICENSE_TEMPLATE")
TEXT_EXT = TEXT_EXT + (".mts", ".cts")
BINARY_EXT = (".pdf", ".ttf", ".otf", ".woff", ".woff2", ".ttc", ".png", ".jpg", ".jpeg", ".gif", ".tif", ".tiff", ".bmp",
              ".webp", ".ico", ".jar", ".zip", ".class", ".gz", ".tgz", ".7z", ".rar", ".exe", ".dll", ".so", ".dylib", ".bin")
EMBEDDED = re.compile(rb"PK\x03\x04|\x7fELF|\xca\xfe\xba\xbe|\xcf\xfa\xed\xfe|\xfe\xed\xfa[\xce\xcf]")
ACTIVE_MARKUP = re.compile(r"(?i)<script\b|javascript:|\bon(load|error|click|mouseover)\s*=|<iframe\b|<object\b|<embed\b")
BLOB_B64 = re.compile(r"[A-Za-z0-9+/]{120,}={0,2}")
BLOB_HEX = re.compile(r"(?:0x)?[0-9a-fA-F]{120,}")

API = {
    "java": [
        ("process", r"Runtime\.getRuntime\(\)\.exec|\bProcessBuilder\b"),
        ("reflection", r"Class\.forName|getDeclared(Method|Field|Constructor)|\.setAccessible\(|URLClassLoader|defineClass|ScriptEngine|MethodHandles"),
        ("network", r"\bInetAddress\b|getHostName|new\s+Socket\b|HttpURLConnection|\bHttpClient\b|new\s+URL\(|URI\.create\(\s*\"https?:"),
        ("environment", r"System\.getenv|System\.getProperty\(\s*\"(user\.|java\.io\.tmpdir|os\.|java\.home|java\.version)|ProcessHandle|ManagementFactory|availableProcessors|Locale\.getDefault|TimeZone\.getDefault"),
        ("filesystem-probe", r"(Files\.(exists|isDirectory|isReadable|list|walk)|new\s+File)\(\s*(Paths?\.(get|of)\(\s*)?\"(/|~|[A-Za-z]:\\\\)"),
        ("deserialize", r"ObjectInputStream|readObject\(|XMLDecoder|\bUnsafe\b"),
        ("time-api", r"LocalDate(Time)?\.now|Instant\.now|System\.currentTimeMillis|System\.nanoTime|new\s+Date\(\)|Calendar\.getInstance"),
        ("exit", r"System\.exit|Runtime\.getRuntime\(\)\.halt"),
    ],
    "python": [
        ("process", r"\bsubprocess\b|os\.system|os\.popen|os\.exec|pty\.spawn"),
        ("reflection", r"\beval\(|\bexec\(|__import__|importlib|\bcompile\(|getattr\(\s*__builtins__"),
        ("network", r"\bsocket\b|urllib|requests\.|http\.client|aiohttp|httpx"),
        ("environment", r"os\.environ|getpass|os\.getlogin|platform\.(node|uname)|socket\.gethostname|expanduser"),
        ("deserialize", r"pickle\.loads?|marshal\.loads?|yaml\.load\((?!.*SafeLoader)|ctypes"),
        ("encoding", r"base64\.(b64|b32|b85)decode|codecs\.decode|bytes\.fromhex|zlib\.decompress"),
        ("time-api", r"datetime\.(now|today|utcnow)|time\.time\(|date\.today"),
    ],
    "js": [
        ("process", r"child_process|\bexecSync\b|\bspawn\(|\bexecFile\("),
        ("reflection", r"\beval\(|new\s+Function\(|require\(\s*[^'\"\s)]|import\(\s*[^'\"\s)]|vm\.run"),
        ("network", r"\bfetch\(|https?\.request|https?\.get\(|net\.(connect|createConnection)|WebSocket|XMLHttpRequest"),
        ("environment", r"process\.env|os\.(homedir|hostname|userInfo)|process\.platform"),
        ("encoding", r"Buffer\.from\([^)]*['\"](base64|hex)['\"]|atob\("),
        ("time-api", r"Date\.now|new\s+Date\(\)"),
    ],
    "shell": [
        ("network", r"\b(curl|wget|nc|ncat|socat|scp|ssh)\b"),
        ("process", r"\beval\b|bash\s+-c|sh\s+-c|\bsudo\b|\bsource\b\s+<\("),
        ("encoding", r"base64\s+(-d|--decode)|xxd\s+-r|openssl\s+enc"),
        ("environment", r"\$HOME|~/\.|/Users/|/home/|\benv\b\s*\|"),
    ],
}
SEVERITY = {"process": "high", "reflection": "medium", "network": "high", "environment": "medium",
            "filesystem-probe": "medium", "deserialize": "high", "encoding": "medium", "time-api": "info", "exit": "medium"}

WORKFLOW = [("pull-request-target", r"pull_request_target", "high"), ("workflow-run", r"\bworkflow_run\b", "high"),
            ("write-permission", r"(contents|packages|actions|id-token|pull-requests|issues|checks|deployments|statuses):\s*write|permissions:\s*write-all", "high"),
            ("secrets", r"secrets\.|secrets:\s*inherit", "high"),
            ("expression-in-run", r"run:.*\$\{\{\s*github\.(event|head_ref)", "high")]
BUILD = [("build-repository", r"<(plugin)?[Rr]epository>|<url>https?://(?!repo\.maven\.apache\.org)|\"registry\"\s*:|index-url|extra-index-url", "high"),
         ("build-plugin-exec", r"exec-maven-plugin|maven-antrun-plugin|frontend-maven-plugin|<executable>|\"(pre|post)?install\"\s*:|\"prepare\"\s*:|\"prepack\"\s*:|cmdclass|build-backend", "high"),
         ("test-config", r"<skip(Tests)?>|<testFailureIgnore>|<excludes?>|<groups>|<excludedGroups>|surefire|failsafe|maven\.test\.skip", "high"),
         ("new-dependency", r"<artifactId>|^\s*\"[@\w./-]+\"\s*:\s*\"(?:[~^]|workspace:|file:|git\+|https?:)|^\s*\"?[A-Za-z0-9_.-]+\"?\s*(==|>=|~=)", "medium"),
         ("dependency-version", r"<[\w.-]*version>|\"version\"\s*:|^\s*\"[@\w./-]+\"\s*:\s*\"\d", "info")]
PII = [("email", r"[A-Za-z0-9._%+-]{1,64}@(?!example\.(com|org)|users\.noreply\.github\.com|noreply\.)[A-Za-z0-9.-]{1,253}\.[A-Za-z]{2,24}"),
       ("phone-kr", r"\b01[016789][- .]?\d{3,4}[- .]?\d{4}\b"),
       ("rrn-shaped", r"\b\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])[- ]?[1-8]\d{6}\b"),
       ("card-shaped", r"\b(?:\d{4}[ -]){3}\d{1,7}\b|\b\d{13,19}\b")]
PDF_ACTIVE = re.compile(rb"/(JavaScript|JS|Launch|EmbeddedFiles?|RichMedia|XFA|SubmitForm|ImportData|OpenAction|AA)\b")


def git(repo: str, *args: str) -> bytes:
    res = subprocess.run(["git", "-C", repo, *GIT_SAFE, *args], capture_output=True)
    if res.returncode != 0:
        raise SystemExit(f"git {' '.join(args[:3])}: {res.stderr.decode(errors='replace')[:300]}")
    return res.stdout


def lang(path: str) -> str | None:
    p = path.lower()
    if p.endswith((".java", ".kt", ".groovy", ".gradle")):
        return "java"
    if p.endswith((".py", ".pyi")) or p.rsplit("/", 1)[-1] in ("setup.py", "conftest.py"):
        return "python"
    if p.endswith((".js", ".mjs", ".cjs", ".ts", ".mts", ".cts", ".tsx", ".jsx")):
        return "js"
    if p.endswith((".sh", ".bash", ".zsh")) or p.startswith(".github/") or p.rsplit("/", 1)[-1].startswith(("Dockerfile", "Makefile")):
        return "shell"
    return None


def lang_of(path: str, data: bytes) -> str | None:
    """The language by extension, else by a shebang line."""
    lg = lang(path)
    if lg or not data.startswith(b"#!"):
        return lg
    first = data.split(b"\n", 1)[0]
    return "python" if b"python" in first else "js" if b"node" in first else "shell"


def looks_text(data: bytes) -> bool:
    if b"\0" in data[:65536]:
        return False
    try:
        data[:65536].decode("utf-8")
        return True
    except UnicodeDecodeError as e:
        return e.start > 65530


def excerpt(s: str) -> str:
    s = HIDDEN.sub(lambda m: f"<U+{ord(m.group(0)):04X}>", s)
    s = "".join(c if c.isprintable() else "?" for c in s.strip())
    return s[:120] + ("…" if len(s) > 120 else "")


def luhn(digits: str) -> bool:
    d = [int(c) for c in digits if c.isdigit()]
    if not 13 <= len(d) <= 19:
        return False
    s = sum(x if i % 2 == 0 else (x * 2 - 9 if x * 2 > 9 else x * 2) for i, x in enumerate(reversed(d)))
    return s % 10 == 0


def pii_hits(text: str) -> list[tuple[str, str]]:
    out = []
    for rule, pat in PII:
        for m in re.finditer(pat, text):
            digits = re.sub(r"\D", "", m.group(0))
            if rule == "card-shaped" and not luhn(m.group(0)):
                continue
            if digits and (len(set(digits[-8:])) <= 1 or digits[-8:] in ("12345678", "00000000")):
                continue
            out.append((rule, m.group(0)))
    return out


def font_license(data: bytes) -> str:
    """'open', 'unknown', or 'unreadable'. Reads the sfnt name table with bounds checks."""
    try:
        if data[:4] in (b"wOFF", b"wOF2", b"ttcf"):
            return "unreadable"
        num = struct.unpack(">H", data[4:6])[0]
        for i in range(min(num, 64)):
            rec = data[12 + 16 * i: 28 + 16 * i]
            if rec[:4] != b"name":
                continue
            off, length = struct.unpack(">II", rec[8:16])
            tab = data[off: off + length]
            count, soff = struct.unpack(">HH", tab[2:6])
            text = []
            for j in range(min(count, 512)):
                pid, _, _, nid, ln, so = struct.unpack(">HHHHHH", tab[6 + 12 * j: 18 + 12 * j])
                if nid in (13, 14):
                    raw = tab[soff + so: soff + so + ln]
                    text.append(raw.decode("utf-16-be", "replace") if pid in (0, 3) else raw.decode("latin-1"))
            t = " ".join(text).lower()
            known = r"scripts\.sil\.org/ofl|openfontlicense\.org|apache\.org/licenses|licensed under the sil open font license" \
                    r"|licensed under the apache license|ubuntu font licen|dedicated to the public domain"
            negated = r"\bnot\b|\bno\b|rights reserved|proprietary|commercial|restricted|without permission"
            return "open" if re.search(known, t) and not re.search(negated, t) else "unknown"
        return "unknown"
    except (struct.error, IndexError, ValueError):
        return "unreadable"


def kind_of(data: bytes) -> str:
    if data.startswith(b"%PDF") or b"%PDF-" in data[:1024]:
        return "pdf"
    if data[:4] in (b"\x00\x01\x00\x00", b"OTTO", b"true", b"wOFF", b"wOF2", b"ttcf"):
        return "font"
    if data.startswith((b"\x89PNG", b"\xff\xd8\xff", b"GIF8", b"RIFF", b"II*\x00", b"MM\x00*")):
        return "image"
    if data.startswith((b"PK\x03\x04", b"\xca\xfe\xba\xbe", b"\x7fELF", b"\xfe\xed\xfa", b"\xcf\xfa\xed\xfe", b"MZ", b"\x1f\x8b", b"7z\xbc\xaf", b"Rar!")):
        return "executable-or-archive"
    if b"\0" in data[:8192]:
        return "binary"
    return "text"


class Scan:
    def __init__(self) -> None:
        self.hits: list[dict] = []
        self.seen: set[tuple] = set()

    def add(self, rule: str, severity: str, path: str, line: int | None = None, text: str = "", decide: bool = False) -> None:
        if (rule, path, line) in self.seen:
            return
        self.seen.add((rule, path, line))
        self.hits.append({"id": f"S{len(self.hits) + 1}", "rule": rule, "severity": severity, "decide": decide,
                          "file": path, "line": line, "excerpt": excerpt(text)})


def scan_diff(repo: str, old: str, new: str) -> Scan:
    sc = Scan()
    raw = git(repo, "diff", "--no-ext-diff", "--no-textconv", "--raw", "-z", "-M", old, new).split(b"\0")
    i, entries = 0, []
    while i < len(raw) and raw[i]:
        meta = raw[i].decode("ascii", "replace").lstrip(":").split()
        status = meta[4]
        if status[0] in "RC":
            entries.append((meta, os.fsdecode(raw[i + 1]), os.fsdecode(raw[i + 2])))
            i += 3
        else:
            entries.append((meta, None, os.fsdecode(raw[i + 1])))
            i += 2
    for meta, oldpath, path in entries:
        omode, nmode, _, nsha, status = meta[0], meta[1], meta[2], meta[3], meta[4][0]
        cat = category(path)
        for p in filter(None, (oldpath, path)):
            c = category(p)
            if c in ("instruction", "workflow", "tooling", "skill"):
                sc.add(f"sensitive-path:{c}", "high" if c in ("instruction", "workflow") else "medium", p, decide=True)
            elif c == "build":
                sc.add("build-file", "info", p)
            if c == "samples" or "/src/test/resources/" in p:
                sc.add("corpus-or-test-resource", "medium", p)
            if re.search(r"/src/test/resources/(junit-platform\.properties|META-INF/services/)|/src/test/resources/.*\.properties$|conftest\.py$|pytest\.ini$|jest\.config|vitest\.config", p):
                sc.add("test-config", "high", p, decide=True)
        if status == "D":
            if cat == "test":
                sc.add("test-deleted", "medium", path)
            continue
        if nmode in ("120000", "160000"):
            sc.add({"120000": "symlink", "160000": "submodule"}[nmode], "high", path, decide=True)
        elif nmode == "100755" and omode != nmode:
            sc.add("exec-bit" if omode != "000000" else "new-executable-file", "medium" if omode != "000000" else "info", path)
        if nmode == "160000":
            continue
        size = int(git(repo, "cat-file", "-s", nsha).strip())
        if size > MAX_BLOB:
            sc.add("too-large-to-scan", "medium", path, text=f"{size} bytes")
            continue
        data = git(repo, "cat-file", "blob", nsha)
        base = path.rsplit("/", 1)[-1]
        text_name = base.lower().endswith(TEXT_EXT) or base.startswith(TEXT_NAMES) or base.lower() in TEXT_EXT
        if text_name or ("." not in base and looks_text(data)) or (not base.lower().endswith(BINARY_EXT) and looks_text(data)):
            if b"\0" in data[:65536]:
                sc.add("binary-in-text-file", "medium", path)
            scan_text_changes(repo, old, new, oldpath or path, path, sc, lang_of(path, data))
            continue
        k = kind_of(data)
        inner = EMBEDDED.search(data, 4)
        if k != "pdf" and inner:
            sc.add("embedded-executable-or-archive", "high", path, text=f"signature at byte {inner.start()}", decide=True)
        if k == "pdf":
            active = sorted({m.group(1).decode() for m in PDF_ACTIVE.finditer(data)})
            hidden = b"/ObjStm" in data
            sc.add("pdf-added", "medium", path, text=f"active keys: {active or 'none'}; object streams: {hidden}; "
                   "text is compressed: extract it with main's CLI in isolation and run 'diff_scan.py pii'")
            if active:
                sc.add("pdf-active-content", "high", path, text=", ".join(active))
            if hidden:
                sc.add("pdf-object-streams", "medium", path, text="dictionary keys can hide in object streams; decode in isolation")
            for rule, val in pii_hits(data.decode("latin-1")):
                sc.add(f"pii:{rule}", "high", path, text=val)
            continue
        if k == "font":
            lic = font_license(data)
            sc.add("font-added", "medium", path, text=f"license fields: {lic}; give the source and the license")
            if lic != "open":
                sc.add(f"font-license-{lic}", "high", path, decide=True)
            continue
        if k in ("image", "binary", "executable-or-archive"):
            sc.add(k if k != "binary" else "binary-file", "high" if k == "executable-or-archive" else "medium", path,
                   decide=k == "executable-or-archive")
            continue
        scan_text_changes(repo, old, new, oldpath or path, path, sc, lang_of(path, data))
    return sc


def scan_text_changes(repo: str, old: str, new: str, oldpath: str, path: str, sc: Scan, lg: str | None = None) -> None:
    patch = git(repo, "diff", *DIFF_SAFE, "-U0", "-M", old, new, "--", f":(literal){oldpath}", f":(literal){path}").decode("utf-8", "replace")
    cat = category(path)
    # A file without a known language and without an extension can be a script: use the rules of all languages.
    rules = API.get(lg or "", []) if lg or "." in path.rsplit("/", 1)[-1] else [r for rs in API.values() for r in rs]
    n = 0
    removed_asserts = 0
    header = False
    for line in patch.split("\n"):
        if line.startswith("diff --git "):
            header = True
            continue
        if line.startswith("@@"):
            header = False
            m = re.search(r"\+(\d+)", line)
            n = int(m.group(1)) if m else 0
            continue
        if header:  # "---" and "+++" are file headers only here: an added line "++i" is "+++i" (#836)
            continue
        if line.startswith("-"):
            if cat == "test" and re.search(r"\bassert|\bexpect\(|\bverify\(", line):
                removed_asserts += 1
            continue
        if not line.startswith("+"):
            continue
        text, ln = line[1:], n
        n += 1
        if len(text) > MAX_LINE:
            sc.add("long-line", "medium", path, ln, f"{len(text)} characters; the rules read the first {MAX_LINE}")
            text = text[:MAX_LINE]
        if HIDDEN.search(text):
            sc.add("hidden-unicode", "high", path, ln, text)
        if INVISIBLE.search(text):
            sc.add("invisible-character", "medium", path, ln, text)
        if VARIATION.search(text):
            sc.add("variation-selector", "info", path, ln, text)
        if ACTIVE_MARKUP.search(text) and path.lower().endswith((".svg", ".html", ".htm", ".xhtml", ".md", ".mdx", ".xml")):
            sc.add("active-markup", "medium", path, ln, text)
        if BLOB_B64.search(text) or BLOB_HEX.search(text):
            sc.add("encoded-blob", "medium", path, ln, text)
        for rule, pat in rules:
            if re.search(pat, text):
                sc.add(f"api:{rule}", SEVERITY[rule], path, ln, text)
        if path.startswith(".github/"):
            for rule, pat, sev in WORKFLOW:
                if re.search(pat, text):
                    sc.add(f"workflow:{rule}", sev, path, ln, text, decide=True)
        if cat == "build":
            for rule, pat, sev in BUILD:
                if re.search(pat, text):
                    sc.add(f"build:{rule}", sev, path, ln, text, decide=rule != "dependency-version")
        if re.search(r"(?i)\b(ignore (all|previous|the above)|you are (an? )?(ai|assistant|reviewer)|as an ai|system prompt|do not (report|mention)|approve this)", text):
            sc.add("text-to-an-ai", "high", path, ln, text)
        for rule, val in pii_hits(text):
            sc.add(f"pii:{rule}", "high", path, ln, val)
    if removed_asserts:
        sc.add("test-assertions-removed", "medium", path, text=f"{removed_asserts} removed assertion lines")


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    d = sub.add_parser("diff")
    d.add_argument("--git", default=".")
    d.add_argument("--old", required=True)
    d.add_argument("--new", required=True)
    d.add_argument("--out", required=True)
    q = sub.add_parser("pii")
    q.add_argument("--out", required=True)
    q.add_argument("files", nargs="+")
    a = p.parse_args(argv)
    if a.cmd == "diff":
        sc = scan_diff(a.git, a.old, a.new)
        result = {"range": [a.old, a.new], "hits": sc.hits}
    else:
        sc = Scan()
        labels = [f"{k}:{f.rsplit('/', 1)[-1]}" for k, f in enumerate(a.files, 1)]  # before/x.txt and after/x.txt stay apart (#836)
        for f, label in zip(a.files, labels):
            with open(f, encoding="utf-8", errors="replace") as fh:
                for i, line in enumerate(fh, 1):
                    for rule, val in pii_hits(line):
                        sc.add(f"pii:{rule}", "high", label, i, val)
        result = {"files": labels, "hits": sc.hits}
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=1)
    by = {}
    for h in sc.hits:
        by[h["severity"]] = by.get(h["severity"], 0) + 1
    print(f"{len(sc.hits)} hits {by}; decide: {sum(h['decide'] for h in sc.hits)}; {a.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
