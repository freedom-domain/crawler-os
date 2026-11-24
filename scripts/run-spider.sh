#!/usr/bin/env bash
# 通过 Maven 启动 crawler-spider-service (爬虫/任务/调度, 端口 8082)。
#
# 用法:
#   ./scripts/run-spider.sh                           # local profile (默认)
#   SPRING_PROFILES=default ./scripts/run-spider.sh   # 指定 profile
#   SKIP_BUILD=1 ./scripts/run-spider.sh              # 跳过构建步骤
#   ./scripts/run-spider.sh -h                        # 查看帮助
set -euo pipefail

MODULE="crawler-spider-service"
SERVICE_NAME="爬虫/任务/调度服务"
PORT=8082

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/service-common.sh"
