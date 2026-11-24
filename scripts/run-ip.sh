#!/usr/bin/env bash
# 通过 Maven 启动 crawler-ip-service (Cloudflare IP 管理与测速, 端口 8086)。
#
# 用法:
#   ./scripts/run-ip.sh                           # local profile (默认)
#   SPRING_PROFILES=default ./scripts/run-ip.sh   # 指定 profile
#   SKIP_BUILD=1 ./scripts/run-ip.sh              # 跳过构建步骤
#   ./scripts/run-ip.sh -h                        # 查看帮助
set -euo pipefail

MODULE="crawler-ip-service"
SERVICE_NAME="Cloudflare IP 管理服务"
PORT=8086

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/service-common.sh"
