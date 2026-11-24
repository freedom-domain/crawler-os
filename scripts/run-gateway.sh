#!/usr/bin/env bash
# 通过 Maven 启动 crawler-gateway (API 网关, 端口 8080)。
#
# 用法:
#   ./scripts/run-gateway.sh                           # local profile (默认)
#   SPRING_PROFILES=default ./scripts/run-gateway.sh   # 指定 profile
#   SKIP_BUILD=1 ./scripts/run-gateway.sh              # 跳过构建步骤
#   ./scripts/run-gateway.sh -h                        # 查看帮助
set -euo pipefail

MODULE="crawler-gateway"
SERVICE_NAME="API 网关"
PORT=8080

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/service-common.sh"
