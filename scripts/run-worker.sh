#!/usr/bin/env bash
# 通过 Maven 启动 crawler-worker (爬虫执行集群, 端口 8090)。
#
# 用法:
#   ./scripts/run-worker.sh                           # local profile (默认)
#   SPRING_PROFILES=default ./scripts/run-worker.sh   # 指定 profile
#   SKIP_BUILD=1 ./scripts/run-worker.sh              # 跳过构建步骤
#   ./scripts/run-worker.sh -h                        # 查看帮助
#
# 注意: worker 依赖 Playwright 浏览器内核渲染 JS 页面,
# 首次运行前需安装一次: mvn com.microsoft.playwright:playwright:1.51.0:install
set -euo pipefail

MODULE="crawler-worker"
SERVICE_NAME="爬虫执行集群"
PORT=8090

preflight() {
  local playwright_cache="${HOME}/.cache/ms-playwright"
  if ! ls "$playwright_cache" >/dev/null 2>&1; then
    echo "警告: 未检测到 Playwright 浏览器内核 ($playwright_cache)。"
    echo "需要 JS 渲染的抓取任务会失败, 请先执行:"
    echo "  mvn com.microsoft.playwright:playwright:1.51.0:install"
  fi
}

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/service-common.sh"
