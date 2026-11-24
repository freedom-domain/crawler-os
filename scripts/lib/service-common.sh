#!/usr/bin/env bash
# =============================================================================
# scripts/run-*.sh 各服务 Maven 启动脚本的共享逻辑。
#
# 包装脚本在 source 本文件之前必须设置:
#   MODULE       Maven 模块目录名, 例如 crawler-gateway (必填)
#   SERVICE_NAME 服务显示名称 (可选, 默认取 MODULE)
#   PORT         服务端口, 仅用于展示 (可选)
#
# 环境变量:
#   SPRING_PROFILES  激活的 Spring profile, 默认 local; 多个用逗号分隔
#   SKIP_BUILD=1     跳过 Maven 构建步骤 (要求模块及其依赖已安装到本地仓库)
#
# 包装脚本可选定义 preflight(), 在构建完成、启动服务前执行。
# =============================================================================

set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-$MODULE}"
profile="${SPRING_PROFILES:-local}"

if ! command -v mvn >/dev/null 2>&1; then
  printf 'Error: Maven (mvn) is not installed or is not on PATH.\n' >&2
  exit 127
fi

maven_info="$(mvn -version 2>&1)" || {
  printf 'Error: failed to inspect the Maven runtime.\n%s\n' "$maven_info" >&2
  exit 1
}

if [[ "$maven_info" =~ Java[[:space:]]version:[[:space:]]([0-9]+)([.]([0-9]+))? ]]; then
  java_major="${BASH_REMATCH[1]}"
  if [[ "$java_major" == "1" && -n "${BASH_REMATCH[3]:-}" ]]; then
    java_major="${BASH_REMATCH[3]}"
  fi
else
  printf 'Error: could not determine the Java version used by Maven.\n%s\n' "$maven_info" >&2
  exit 1
fi

if ((java_major < 21)); then
  printf 'Error: Maven is running with Java %s; this project requires JDK 21 or newer.\n' \
    "$java_major" >&2
  printf 'Set JAVA_HOME to a JDK 21+ installation and ensure "$JAVA_HOME/bin" is on PATH.\n' >&2
  printf 'Maven runtime details:\n%s\n' "$maven_info" >&2
  exit 1
fi

IFS= read -r maven_version <<<"$maven_info" || true

case "${1:-}" in
  -h|--help)
    cat <<EOF
用法: $(basename "$0") [Maven 参数...]

通过 Maven (spring-boot:run) 启动 $MODULE。

选项:
  -h, --help   显示本帮助
  其余参数原样透传给 Maven, 例如:
    $0 -Dserver.port=9080

环境变量:
  SPRING_PROFILES   激活的 Spring profile (默认: local)
  SKIP_BUILD=1      跳过 Maven 构建步骤 (模块及依赖需已 install)
EOF
    exit 0
    ;;
esac

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

echo "==============================================================="
echo " 启动 $SERVICE_NAME ($MODULE)"
echo " 端口    : ${PORT:-?}"
echo " Profile : $profile"
echo " 运行环境: $maven_version (Java $java_major)"
echo "==============================================================="

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  printf '正在构建 %s 及其依赖 (首次运行需要下载依赖, 可能耗时较长)...\n' "$MODULE"
  mvn -B -q -DskipTests -pl "$MODULE" -am install
else
  echo '已跳过 Maven 构建步骤 (SKIP_BUILD=1)。'
fi

if declare -F preflight >/dev/null 2>&1; then
  preflight
fi

# 与 Docker 入口保持一致: -Dnacos.logging.default.config.enabled=false
exec mvn -B -pl "$MODULE" spring-boot:run \
  -Dspring-boot.run.profiles="$profile" \
  -Dspring-boot.run.jvmArguments="-Dnacos.logging.default.config.enabled=false" \
  "$@"
