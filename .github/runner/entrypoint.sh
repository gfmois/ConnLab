#!/usr/bin/env bash
# Registers the runner the first time (with RUNNER_TOKEN) and starts it. The
# registration is kept in the home volume, so later starts need no token.
set -euo pipefail
REPO_URL=https://github.com/gfmois/ConnLab
CONFIG="$HOME/.runner-config"
FILES=(.runner .credentials .credentials_rsaparams)

mkdir -p "$CONFIG"

if [ -f "$CONFIG/.runner" ]; then
  for f in "${FILES[@]}"; do cp "$CONFIG/$f" .; done
else
  if [ -z "${RUNNER_TOKEN:-}" ]; then
    echo "First start: set RUNNER_TOKEN to a registration token (Settings → Actions → Runners → New self-hosted runner)." >&2
    exit 1
  fi
  ./config.sh --unattended --replace --url "$REPO_URL" --token "$RUNNER_TOKEN" \
    --name "${RUNNER_NAME:-$(hostname)}" --labels connlab --work "$HOME/work"
  for f in "${FILES[@]}"; do cp "$f" "$CONFIG/"; done
fi
exec ./run.sh
