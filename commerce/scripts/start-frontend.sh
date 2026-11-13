#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../data-agent-frontend-nuxt"
pnpm install --frozen-lockfile
exec pnpm dev
