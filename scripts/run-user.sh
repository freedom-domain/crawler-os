#!/usr/bin/env bash
# 通过 Maven 启动 crawler-user-service (用户/角色/权限, 端口 8081)。
#
# 用法:
#   ./scripts/run-user.sh                           # local profile (默认)
#   SPRING_PROFILES=default ./scripts/run-user.sh   # 指定 profile
#   SKIP_BUILD=1 ./scripts/run-user.sh              # 跳过构建步骤
#   ./scripts/run-user.sh -h                        # 查看帮助
set -euo pipefail

MODULE="crawler-user-service"
SERVICE_NAME="用户/角色/权限服务"
PORT=8081

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/service-common.sh"
