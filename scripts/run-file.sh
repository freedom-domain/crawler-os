#!/usr/bin/env bash
# 通过 Maven 启动 crawler-file-service (文件管理, 端口 8084)。
#
# 用法:
#   ./scripts/run-file.sh                           # local profile (默认)
#   SPRING_PROFILES=default ./scripts/run-file.sh   # 指定 profile
#   SKIP_BUILD=1 ./scripts/run-file.sh              # 跳过构建步骤
#   ./scripts/run-file.sh -h                        # 查看帮助
set -euo pipefail

MODULE="crawler-file-service"
SERVICE_NAME="文件管理服务"
PORT=8084

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/service-common.sh"
