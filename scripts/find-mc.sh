#!/usr/bin/env bash
# find-mc.sh — grep the DECOMPILED Minecraft source. This is the ground truth for MC APIs.
# Usage:
#   scripts/find-mc.sh Creeper                 # find declarations/usages of "Creeper"
#   scripts/find-mc.sh 'class .*Screen'        # regex
#   scripts/find-mc.sh -f Blocks               # -f: find the FILE(s) named like this
#
# If .agent-docs/mc-src is empty, run scripts/sync-docs.sh first.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/.agent-docs/mc-src"
[ -d "$SRC" ] || { echo "No $SRC. Run scripts/sync-docs.sh first." >&2; exit 1; }

if [ "${1:-}" = "-f" ]; then
	shift
	find "$SRC" -name "*$1*.java" | sed "s#$SRC/##"
	exit 0
fi
[ $# -ge 1 ] || { echo "usage: find-mc.sh [-f] <pattern>" >&2; exit 2; }
# ripgrep if available (fast), else grep.
if command -v rg >/dev/null 2>&1; then
	rg -n --type java "$@" "$SRC" | sed "s#$SRC/##"
else
	grep -rn --include='*.java' "$1" "$SRC" | sed "s#$SRC/##"
fi
