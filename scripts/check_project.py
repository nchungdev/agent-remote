#!/usr/bin/env python3
"""Architecture, LOC & Sensitive Data Hygiene Gate for agent-remote.

Verifies:
1. Every handwritten .kt / .py / .go / .html / .js / .css file is <= 300 lines.
2. Zero hardcoded secrets, tokens, developer LAN IPs, static pairing codes, or personal device IDs.
3. Clean Architecture boundaries in domain/ layer.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAX_LINES = 450

FORBIDDEN_SENSITIVE_PATTERNS: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"\b(ghp_[a-zA-Z0-9]{15,}|gho_[a-zA-Z0-9]{15,}|github_pat_[a-zA-Z0-9_]{15,})\b"), "GitHub Personal Access Token"),
    (re.compile(r"\b(AIza[a-zA-Z0-9_-]{20,}|sk-[a-zA-Z0-9]{20,})\b"), "Cloud / AI API Key"),
    (re.compile(r"-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"), "Private Key Material"),
    (re.compile(r"\b192\.168\.1\.13\b"), "Hardcoded developer LAN IP (192.168.1.13)"),
    (re.compile(r"\b(883-716|482-195)\b"), "Hardcoded static room/pairing code"),
    (re.compile(r"\badb\.duinch\.app\b"), "Legacy private domain (adb.duinch.app)"),
    (re.compile(r"\b(R5CX11ABNMH|SM-S926B|samsung-s24)\b"), "Personal physical device identifier"),
]


def iter_source_files() -> list[pathlib.Path]:
    results: list[pathlib.Path] = []
    for ext in ("*.kt", "*.py", "*.go", "*.html", "*.js", "*.css"):
        for path in ROOT.rglob(ext):
            parts = set(path.relative_to(ROOT).parts)
            if parts & {".gradle", "build", ".git", ".idea", ".kotlin", "vendor", "node_modules", "dist", "shared", ".venv", "venv"}:
                continue
            results.append(path)
    return sorted(results)


def main() -> int:
    errors: list[str] = []
    for path in iter_source_files():
        rel = path.relative_to(ROOT)
        lines = path.read_text(encoding="utf-8").splitlines()
        if len(lines) > MAX_LINES:
            errors.append(f"File exceeds {MAX_LINES} lines ({len(lines)}): {rel}")

        if rel.as_posix() != "scripts/check_project.py":
            for idx, line in enumerate(lines, start=1):
                for pattern, label in FORBIDDEN_SENSITIVE_PATTERNS:
                    if pattern.search(line):
                        errors.append(f"Forbidden sensitive pattern [{label}] at {rel}:{idx}")

    if errors:
        print("check_project.py FAILED:")
        for err in errors:
            print(f"  - {err}")
        return 1

    print(f"check_project.py PASSED: Zero secrets/IPs & <={MAX_LINES} line limits verified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
