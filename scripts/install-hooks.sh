#!/bin/sh
# Points git at the versioned hooks in scripts/hooks. Run once per clone: sh scripts/install-hooks.sh
set -eu
repo_root=$(git rev-parse --show-toplevel)
cd "$repo_root"
chmod +x scripts/hooks/* 2>/dev/null || true
git config core.hooksPath scripts/hooks
echo "Git hooks installed (core.hooksPath = scripts/hooks)."
