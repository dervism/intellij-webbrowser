#!/usr/bin/env bash
#
# release.sh — build, tag, and publish a GitHub release for the
#              Web Browser Panel plugin.
#
# Order is deliberate: we BUILD FIRST and only touch git (commit, tag, push)
# once the build and tests pass. A failing build therefore leaves your repo
# exactly as it was — no stray commit, no tag, nothing pushed.
#
# What it does, in order:
#   1. Pre-flight: confirm we're in the repo, on a branch, with `gh` ready.
#   2. Read the version from build.gradle.kts (the single source of truth),
#      so the tag and the zip always agree.
#   3. Refuse if that tag already exists (locally or on origin).
#   4. If the tree is dirty, ASK now whether it should be committed — but
#      don't commit yet.
#   5. ./gradlew clean check domainTest buildPlugin  (must pass to continue).
#   6. Only now: commit (if you agreed), tag vX.Y.Z, push branch + tag.
#   7. Create the GitHub release with the CHANGELOG's top section as notes,
#      attaching the built plugin zip as a downloadable asset.
#
# Usage:
#   ./scripts/release.sh           # interactive (asks before committing)
#   ./scripts/release.sh -y        # assume "yes" to prompts (hands-off)
#   ./scripts/release.sh --draft   # create the GitHub release as a draft
#
# Prerequisites (one-time):
#   • GitHub CLI installed:  https://cli.github.com
#   • Authenticated:         gh auth login
#
set -euo pipefail

# ---- options ---------------------------------------------------------------
ASSUME_YES=0
DRAFT=0
for arg in "$@"; do
  case "$arg" in
    -y|--yes)  ASSUME_YES=1 ;;
    --draft)   DRAFT=1 ;;
    -h|--help) grep '^#' "$0" | grep -v '^#!' | sed 's/^#\{1,\} \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $arg (try --help)" >&2; exit 2 ;;
  esac
done

# ---- helpers ---------------------------------------------------------------
info() { printf '\033[1;34m▶ %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m! %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

confirm() {
  [ "$ASSUME_YES" = 1 ] && return 0
  printf '%s [y/N] ' "$1"
  local reply=""
  read -r reply || true
  [ "$reply" = y ] || [ "$reply" = Y ]
}

# ---- stage-aware cleanup: on any abnormal exit, say exactly where we are ----
# STAGE advances as we go; the trap reads it to print accurate recovery hints
# and to confirm what was (and wasn't) changed. This is what makes a failed
# build — or a failure at any later step — exit cleanly and legibly.
STAGE="starting"
TAG=""
NOTES_FILE=""
cleanup() {
  local rc=$?
  [ -n "$NOTES_FILE" ] && rm -f "$NOTES_FILE"
  [ "$rc" -eq 0 ] && return
  echo >&2
  case "$STAGE" in
    build)
      die "Build/tests failed — your repo is untouched (no commit, tag, push, or release). Fix and re-run." ;;
    commit)
      die "Failed while committing — nothing was tagged or pushed." ;;
    push)
      warn "Push failed. A local tag '$TAG' may now exist but was not pushed."
      warn "Undo it with:  git tag -d $TAG"
      exit "$rc" ;;
    release)
      warn "Tag '$TAG' was pushed, but creating the GitHub release failed."
      warn "Retry just the release:  gh release create $TAG build/distributions/*-${TAG#v}.zip --title \"Web Browser Panel $TAG\""
      warn "…or roll the tag back:    git push origin :refs/tags/$TAG && git tag -d $TAG"
      exit "$rc" ;;
    *)
      die "Aborted (exit $rc)." ;;
  esac
}
trap cleanup EXIT

# ---- run from the repo root, wherever this script was invoked from ----------
cd "$(dirname "$0")/.."

# ---- pre-flight ------------------------------------------------------------
info "Pre-flight checks"
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "Not a git repository."
command -v gh >/dev/null 2>&1 || die "GitHub CLI (gh) not found — install: https://cli.github.com"
gh auth status >/dev/null 2>&1 || die "gh is not authenticated — run: gh auth login"
BRANCH="$(git branch --show-current)"
[ -n "$BRANCH" ] || die "Detached HEAD — check out a branch first."
ok "On branch '$BRANCH', gh authenticated"

# ---- version + tag (version comes from build.gradle.kts) -------------------
VERSION="$(grep -E '^version[[:space:]]*=' build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
[ -n "$VERSION" ] || die "Could not read 'version = \"...\"' from build.gradle.kts"
TAG="v$VERSION"
info "Releasing $TAG"

if git rev-parse "$TAG" >/dev/null 2>&1; then
  die "Tag $TAG already exists locally — bump the version in build.gradle.kts first."
fi
if git ls-remote --exit-code --tags origin "$TAG" >/dev/null 2>&1; then
  die "Tag $TAG already exists on origin — bump the version first."
fi

# ---- decide on the commit now, but DON'T commit until the build passes ------
WILL_COMMIT=0
if [ -n "$(git status --porcelain)" ]; then
  info "Working tree has uncommitted changes:"
  git status --short
  if confirm "After the build passes, commit ALL of the above as \"Release $TAG\"?"; then
    WILL_COMMIT=1
  else
    die "The tree must be clean (or committed) before tagging — aborting."
  fi
fi

# ---- build + test FIRST (a failure here leaves git completely untouched) ----
STAGE="build"
info "Building: ./gradlew clean check domainTest buildPlugin"
./gradlew clean check domainTest buildPlugin
ok "Build + tests passed"

# ---- locate the artifact (build/ is gitignored, so it won't be committed) ---
shopt -s nullglob
ZIPS=(build/distributions/*-"$VERSION".zip)
shopt -u nullglob
[ "${#ZIPS[@]}" -eq 1 ] || die "Expected one build/distributions/*-$VERSION.zip, found ${#ZIPS[@]}."
ZIP="${ZIPS[0]}"
ok "Artifact: $ZIP"

# ---- now mutate git: commit (if agreed), tag, push --------------------------
if [ "$WILL_COMMIT" = 1 ]; then
  STAGE="commit"
  git add -A
  git commit -m "Release $TAG"
  ok "Committed working tree"
fi

STAGE="push"
info "Pushing '$BRANCH' and tag '$TAG'"
git push origin "$BRANCH"
git tag -a "$TAG" -m "Release $TAG"
git push origin "$TAG"
ok "Tag pushed"

# ---- GitHub release: notes = the top section of CHANGELOG.md ----------------
STAGE="release"
NOTES_FILE="$(mktemp)"
# Print from the first "## [" heading up to (but not including) the next one.
awk '/^## \[/{c++} c==1' CHANGELOG.md > "$NOTES_FILE"
[ -s "$NOTES_FILE" ] || echo "Release $TAG" > "$NOTES_FILE"

info "Creating GitHub release"
GH_ARGS=("$TAG" "$ZIP" --title "Web Browser Panel $TAG" --notes-file "$NOTES_FILE")
[ "$DRAFT" = 1 ] && GH_ARGS+=(--draft)
gh release create "${GH_ARGS[@]}"

STAGE="done"
URL="$(gh release view "$TAG" --json url -q .url 2>/dev/null || true)"
ok "Released $TAG"
[ -n "$URL" ] && echo "   $URL"

cat <<EOF

Done. The zip is attached to the GitHub release above.

Separate step — publish to the JetBrains Marketplace (GitHub ≠ Marketplace):
  • Web UI:  https://plugins.jetbrains.com  →  your plugin  →  "Upload update"
             and select:  $ZIP
  • Or Gradle (needs a token):  PUBLISH_TOKEN=xxx ./gradlew publishPlugin
EOF
