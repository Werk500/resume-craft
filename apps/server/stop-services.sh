#!/usr/bin/env bash
# ============================================================================
# ResumeCraft 微服务停止脚本（Linux / WSL）
#
# 用法：
#   ./stop-services.sh          # 优雅停止（SIGTERM，等 20s，超时才 SIGKILL）
#   ./stop-services.sh --force  # 直接 SIGKILL（进程卡死、日志都不写了的时候用）
#
# 设计要点：
#   · 优先读 logs/<服务>.pid —— 精确终止我们自己启动的进程，不会误杀别人的 java
#   · PID 文件丢了就按端口兜底（ss -lntp 找 PID），保证"脚本能善后"
#   · 先 SIGTERM 后 SIGKILL：给 Spring Boot 机会跑优雅关停（释放连接、刷盘）
# ============================================================================
set -uo pipefail

cd "$(dirname "$0")" || exit 1
PID_DIR="logs"
STOP_TIMEOUT="${STOP_TIMEOUT:-20}"
FORCE=0
[ "${1:-}" = "--force" ] && FORCE=1

SERVICES=(
    "gateway|8080"
    "application-service|8084"
    "job-match-service|8083"
    "resume-service|8082"
    "auth-service|8081"
)

pid_on_port() {
    ss -lntp 2>/dev/null | awk -v p=":$1\$" '$4 ~ p' | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2
}

stop_one() {
    local name=$1 port=$2 pid=""
    local pidfile="$PID_DIR/$name.pid"

    if [ -f "$pidfile" ]; then
        pid=$(cat "$pidfile")
        rm -f "$pidfile"
    else
        pid=$(pid_on_port "$port")
        [ -n "$pid" ] && echo "[INFO] $name 没有 PID 文件，按端口 $port 找到 pid=$pid"
    fi

    if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null; then
        echo "[SKIP] $name 未在运行"
        return 0
    fi

    if [ "$FORCE" = "1" ]; then
        echo "[KILL] $name pid=$pid  SIGKILL"
        kill -9 "$pid" 2>/dev/null
        return 0
    fi

    echo "[STOP] $name pid=$pid  发送 SIGTERM，最多等 ${STOP_TIMEOUT}s 优雅退出…"
    kill -15 "$pid" 2>/dev/null
    for _ in $(seq 1 "$STOP_TIMEOUT"); do
        kill -0 "$pid" 2>/dev/null || { echo "[DONE] $name 已退出"; return 0; }
        sleep 1
    done

    echo "[WARN] $name 未在 ${STOP_TIMEOUT}s 内退出，改用 SIGKILL"
    kill -9 "$pid" 2>/dev/null
}

for entry in "${SERVICES[@]}"; do
    IFS='|' read -r name port <<< "$entry"
    stop_one "$name" "$port"
done

echo
echo "剩余监听端口检查："
ss -lntp 2>/dev/null | grep -E ':(808[0-4])\b' || echo "  8080~8084 都没有进程在监听了 ✔"
