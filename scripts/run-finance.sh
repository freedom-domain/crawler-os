#!/usr/bin/env bash
# 通过 Maven 启动 crawler-finance-service (模拟投资/基金行情, 端口 8085)。
#
# 用法:
#   ./scripts/run-finance.sh                           # local profile (默认)
#   SPRING_PROFILES=default ./scripts/run-finance.sh   # 指定 profile
#   SKIP_BUILD=1 ./scripts/run-finance.sh              # 跳过构建步骤
#   ./scripts/run-finance.sh -h                        # 查看帮助
set -euo pipefail

MODULE="crawler-finance-service"
SERVICE_NAME="模拟投资/基金行情服务"
PORT=8085

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/service-common.sh"
