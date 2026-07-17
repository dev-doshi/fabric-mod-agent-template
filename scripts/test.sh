#!/usr/bin/env bash
# test.sh — tiered test loop. Exit code is the source of truth; no log scraping needed.
#
#   scripts/test.sh unit       # Tier 1: JUnit (seconds)     — logic, config, codecs
#   scripts/test.sh game       # Tier 2: server @GameTest    — block/item/entity/command behavior
#   scripts/test.sh client     # Tier 3: client gametest     — GUI, input, screenshots
#   scripts/test.sh            # Tier 1 + 2 (fast inner loop; default)
#   scripts/test.sh all        # 1 + 2 + 3
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
G="./gradlew --console=plain"

case "${1:-fast}" in
	unit)   $G test ;;
	game)   $G runGameTest ;;
	client) $G runClientGameTest ;;
	fast)   $G test runGameTest ;;
	all)    $G test runGameTest runClientGameTest ;;
	*) echo "usage: test.sh [unit|game|client|fast|all]" >&2; exit 2 ;;
esac
