#!/usr/bin/env bash
#
# publish.sh — upload the released Web Browser Panel plugin to the
#              JetBrains Marketplace.
#
# Run it after scripts/release.sh. It publishes the version in build.gradle.kts,
# and only when the plugin's sources and build files are exactly as they were in
# that version's release, so the Marketplace gets what the GitHub release has.
# Later commits that only touch docs or scripts are fine.
#
# What it does, in order:
#   1. Pre-flight: read the version from build.gradle.kts, and check that the
#      vX.Y.Z tag exists, HEAD comes after it, nothing in src/ or the Gradle
#      build files changed since, and the working tree is clean.
#   2. Check that the Marketplace token is in the macOS Keychain (without reading
#      it yet), unless PUBLISH_TOKEN is already set.
#   3. Ask before uploading: publishing is public and can't be undone.
#   4. Read the token and run
#        ./gradlew clean domainTest buildPlugin verifyPlugin publishPlugin
#      with PUBLISH_TOKEN set for that one command. The token is never printed.
#
# Usage:
#   ./scripts/publish.sh        # interactive (asks before uploading)
#   ./scripts/publish.sh -y     # assume "yes" (hands-off)
#
# The token (one-time setup):
#   security add-generic-password -a "$USER" -s jetbrains-marketplace -w
#   (prompts for the token). Set KEYCHAIN_SERVICE to use another item name, or
#   set PUBLISH_TOKEN yourself to skip the Keychain.
#
set -euo pipefail

# ---- options ---------------------------------------------------------------
ASSUME_YES=0
for arg in "$@"; do
  case "$arg" in
    -y|--yes)  ASSUME_YES=1 ;;
    -h|--help) grep '^#' "$0" | grep -v '^#!' | sed 's/^#\{1,\} \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $arg (try --help)" >&2; exit 2 ;;
  esac
done

KEYCHAIN_SERVICE="${KEYCHAIN_SERVICE:-jetbrains-marketplace}"

# ---- helpers ---------------------------------------------------------------
info() { printf '\033[1;34m▶ %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

confirm() {
  [ "$ASSUME_YES" = 1 ] && return 0
  printf '%s [y/N] ' "$1"
  local reply=""
  read -r reply || true
  [ "$reply" = y ] || [ "$reply" = Y ]
}

# ---- run from the repo root, wherever this script was invoked from ----------
cd "$(dirname "$0")/.."

# ---- pre-flight: the version and its release commit -------------------------
info "Pre-flight checks"
VERSION="$(grep -E '^version[[:space:]]*=' build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
[ -n "$VERSION" ] || die "Could not read 'version = \"...\"' from build.gradle.kts"
TAG="v$VERSION"

# What goes into the plugin zip. Anything else (docs, scripts) may change after a release.
BUILD_INPUTS=(src build.gradle.kts settings.gradle.kts gradle.properties gradle)

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null \
  || die "Tag $TAG doesn't exist. Release it first with ./scripts/release.sh."
git merge-base --is-ancestor "$TAG" HEAD \
  || die "HEAD doesn't include the $TAG release. Check out $TAG or a later commit."
git diff --quiet "$TAG" HEAD -- "${BUILD_INPUTS[@]}" \
  || die "The plugin's sources or build files changed since $TAG. Release a new version first, or check out $TAG."
[ -z "$(git status --porcelain)" ] \
  || die "The working tree has uncommitted changes. Commit or stash them first."
ok "Sources and build files match $TAG, tree is clean"

# ---- pre-flight: the token is available (not read yet) ----------------------
if [ -z "${PUBLISH_TOKEN:-}" ]; then
  command -v security >/dev/null 2>&1 \
    || die "No PUBLISH_TOKEN set and the macOS 'security' tool isn't available."
  security find-generic-password -s "$KEYCHAIN_SERVICE" >/dev/null 2>&1 \
    || die "No Keychain item '$KEYCHAIN_SERVICE'. Add it with: security add-generic-password -a \"\$USER\" -s $KEYCHAIN_SERVICE -w"
  ok "Marketplace token found in the Keychain ('$KEYCHAIN_SERVICE')"
else
  ok "Using PUBLISH_TOKEN from the environment"
fi

# ---- confirm: publishing is public and permanent ----------------------------
confirm "Publish Web Browser Panel $VERSION to the JetBrains Marketplace? It's public and can't be undone." \
  || die "Not published."

# ---- read the token, then build, verify and upload --------------------------
# Skips `check`: its platform tests fail on the local IntelliJ 2026.2.3 because
# of a bug in the IDE's bundled Ultimate plugin (see "Known issue" in CLAUDE.md),
# the same as release.sh.
TOKEN="${PUBLISH_TOKEN:-}"
if [ -z "$TOKEN" ]; then
  TOKEN="$(security find-generic-password -s "$KEYCHAIN_SERVICE" -w)" \
    || die "Couldn't read the token from the Keychain (access denied?)."
fi
[ -n "$TOKEN" ] || die "The Marketplace token is empty."

info "Building, verifying and publishing $VERSION"
PUBLISH_TOKEN="$TOKEN" ./gradlew clean domainTest buildPlugin verifyPlugin publishPlugin
unset TOKEN

ok "Published Web Browser Panel $VERSION to the JetBrains Marketplace"
echo "   Check its status under your plugin on https://plugins.jetbrains.com"
