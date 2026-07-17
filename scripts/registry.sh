#!/usr/bin/env bash
# registry.sh — list real Minecraft registry ids (blocks, items, entity_type, ...) from mcmeta.
# Answers "is minecraft:X a real id?" without guessing.
# Usage:
#   scripts/registry.sh                         # list all registry keys
#   scripts/registry.sh block                   # list all block ids
#   scripts/registry.sh entity_type | grep zomb # filter
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MCMETA="$ROOT/.agent-docs/mcmeta"
[ -d "$MCMETA" ] || { echo "No $MCMETA. Run scripts/sync-docs.sh first." >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "jq not installed (brew install jq)." >&2; exit 1; }

if [ $# -eq 0 ]; then
	# registries branch: one dir per registry key.
	find "$MCMETA" -name data.json -path '*/*/data.json' | sed "s#$MCMETA/##;s#/data.json##" | sort
	exit 0
fi
KEY="$1"
FILE="$MCMETA/$KEY/data.json"
[ -f "$FILE" ] || { echo "No registry '$KEY'. Run scripts/registry.sh with no args to list keys." >&2; exit 1; }
# mcmeta registries files are a JSON array of ids (namespaced).
jq -r '.[]' "$FILE" 2>/dev/null || jq -r 'keys[]' "$FILE"
