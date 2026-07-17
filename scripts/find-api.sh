#!/usr/bin/env bash
# find-api.sh — grep the Fabric API source (all 45 modules). Ground truth for Fabric hooks/events/registries.
# Usage: scripts/find-api.sh CreativeModeTabEvents
#        scripts/find-api.sh -f GameTest        # -f: find files named like this
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/.agent-docs/fabric-api"
[ -d "$SRC" ] || { echo "No $SRC. Run scripts/sync-docs.sh first." >&2; exit 1; }

if [ "${1:-}" = "-f" ]; then
	shift
	find "$SRC" -name "*$1*.java" -not -path '*/build/*' | sed "s#$SRC/##"
	exit 0
fi
[ $# -ge 1 ] || { echo "usage: find-api.sh [-f] <pattern>" >&2; exit 2; }
if command -v rg >/dev/null 2>&1; then
	rg -n --type java -g '!**/build/**' "$@" "$SRC" | sed "s#$SRC/##"
else
	grep -rn --include='*.java' "$1" "$SRC" | sed "s#$SRC/##"
fi
