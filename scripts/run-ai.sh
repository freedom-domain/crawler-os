#!/usr/bin/env bash
# 通过 Maven 启动 crawler-ai-service (AI/RAG, 端口 8087)。
#
# 用法:
#   ./scripts/run-ai.sh                           # local profile (默认)
#   SPRING_PROFILES=default ./scripts/run-ai.sh   # 指定 profile
#   SKIP_BUILD=1 ./scripts/run-ai.sh              # 跳过构建步骤
#   ./scripts/run-ai.sh -h                        # 查看帮助
set -euo pipefail

MODULE="crawler-ai-service"
SERVICE_NAME="AI/RAG 服务"
PORT=8087

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/service-common.sh"
