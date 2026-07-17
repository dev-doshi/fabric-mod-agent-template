#!/usr/bin/env bash
# sync-docs.sh — build the offline grounding corpus under .agent-docs/ (gitignored).
# Idempotent: re-run any time to refresh. Version-pinned to this repo's gradle.properties.
#
# WHY THIS EXISTS: LLMs hallucinate Minecraft APIs badly. This vendors the GROUND TRUTH
# (decompiled MC source + Fabric source + docs) so the agent greps facts instead of guessing.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCS="$ROOT/.agent-docs"
cd "$ROOT"

# Pull versions from gradle.properties (single source of truth).
prop() { grep -E "^$1=" gradle.properties | cut -d= -f2- ; }
MC_VERSION="$(prop minecraft_version)"
API_VERSION="$(prop fabric_api_version)"

echo "== sync-docs: MC=$MC_VERSION  fabric-api=$API_VERSION =="
mkdir -p "$DOCS"

# --- 1. Decompiled Minecraft source — the PRIMARY oracle -----------------------
# Since MC 26.1 is unobfuscated, the "deobf" jars Loom produces already carry real class names.
# Loom 1.17 stores decompiled output in a content-addressed cache (not a grep-friendly
# `-sources.jar`), so we decompile the deobf jars DIRECTLY with Vineflower into a clean tree.
# `genSources` still runs first: it materializes the deobf jars and pulls the Vineflower jar.
echo "== [1/6] genSources (materialize deobf jars + Vineflower — first run slow, minutes) =="
./gradlew genSources --console=plain
GC="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"
VF="$(find "$GC/modules-2" -name 'vineflower-*.jar' 2>/dev/null | sort -V | tail -1)"
[ -n "$VF" ] || { echo "!! Vineflower jar not found in Gradle cache." >&2; }
echo "== decompiling deobf jars with $(basename "$VF") into .agent-docs/mc-src =="
rm -rf "$DOCS/mc-src"; mkdir -p "$DOCS/mc-src"
found=0
while IFS= read -r jar; do
	base="$(basename "$jar" .jar)"
	case "$base" in
		*-common-deobf-*) out="$DOCS/mc-src/common" ;;
		*-clientonly-deobf-*) out="$DOCS/mc-src/client" ;;
		*) continue ;;
	esac
	mkdir -p "$out"
	java -jar "$VF" -dgs=1 -rsy=1 "$jar" "$out" > "$out/../vineflower-$(basename "$out").log" 2>&1
	found=1
done < <(find "$GC/fabric-loom/minecraftMaven" -name '*-deobf-*.jar' ! -name '*.backup' 2>/dev/null)
# Vineflower writes a jar of .java; extract it in place.
find "$DOCS/mc-src" -name '*.jar' -exec sh -c 'unzip -oq "$1" -d "$(dirname "$1")" && rm -f "$1"' _ {} \;
[ "$found" = 1 ] || echo "!! No deobf jars found. Check ./gradlew genSources succeeded." >&2
echo "   mc-src java files: $(find "$DOCS/mc-src" -name '*.java' | wc -l | tr -d ' ')"

# --- 2. Official Fabric docs (sparse: develop/ + reference/) --------------------
echo "== [2/6] fabric-docs (sparse develop + reference) =="
if [ -d "$DOCS/fabric-docs/.git" ]; then
	git -C "$DOCS/fabric-docs" pull --ff-only || true
else
	rm -rf "$DOCS/fabric-docs"
	git clone --depth 1 --filter=blob:none --sparse https://github.com/FabricMC/fabric-docs.git "$DOCS/fabric-docs"
	git -C "$DOCS/fabric-docs" sparse-checkout set develop reference players index.md
fi

# --- 3. Fabric API source (matches our MC version) -----------------------------
echo "== [3/6] fabric-api source =="
if [ -d "$DOCS/fabric-api/.git" ]; then
	git -C "$DOCS/fabric-api" fetch --depth 1 origin "$MC_VERSION" && git -C "$DOCS/fabric-api" checkout -q FETCH_HEAD || true
else
	rm -rf "$DOCS/fabric-api"
	git clone --depth 1 -b "$MC_VERSION" https://github.com/FabricMC/fabric-api.git "$DOCS/fabric-api" \
		|| git clone --depth 1 https://github.com/FabricMC/fabric-api.git "$DOCS/fabric-api"
fi

# --- 4. Canonical example mod (buildscript reference) --------------------------
echo "== [4/6] fabric-example-mod =="
rm -rf "$DOCS/example-mod"
git clone --depth 1 -b "$MC_VERSION" https://github.com/FabricMC/fabric-example-mod.git "$DOCS/example-mod" \
	|| git clone --depth 1 https://github.com/FabricMC/fabric-example-mod.git "$DOCS/example-mod"

# --- 5. mcmeta registries (every registry id, machine-readable, 0.2MB) ----------
echo "== [5/6] mcmeta registries =="
rm -rf "$DOCS/mcmeta"
git clone --depth 1 -b registries https://github.com/misode/mcmeta.git "$DOCS/mcmeta"

# --- 6. Mixin + MixinExtras wikis ----------------------------------------------
echo "== [6/6] mixin + mixinextras wikis =="
rm -rf "$DOCS/mixin-wiki" "$DOCS/mixinextras-wiki"
git clone --depth 1 https://github.com/SpongePowered/Mixin.wiki.git "$DOCS/mixin-wiki" || true
git clone --depth 1 https://github.com/LlamaLad7/MixinExtras.wiki.git "$DOCS/mixinextras-wiki" || true

echo ""
echo "== DONE. Corpus at $DOCS =="
du -sh "$DOCS"/* 2>/dev/null || true
echo "See .agent-docs/INDEX.md for grep recipes."
