#!/usr/bin/env bash
# 通过 Maven 启动 crawler-search-service (数据搜索, 端口 8083)。
#
# 用法:
#   ./scripts/run-search.sh                           # local profile (默认)
#   SPRING_PROFILES=default ./scripts/run-search.sh   # 指定 profile
#   SKIP_BUILD=1 ./scripts/run-search.sh              # 跳过构建步骤
#   ./scripts/run-search.sh -h                        # 查看帮助
set -euo pipefail

MODULE="crawler-search-service"
SERVICE_NAME="数据搜索服务"
PORT=8083

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/service-common.sh"
