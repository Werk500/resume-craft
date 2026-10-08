#!/usr/bin/env bash
# ============================================================================
# ResumeCraft 微服务启动脚本（Linux / WSL）
#
# 用法：
#   ./start-services.sh              # 启动 5 个服务（后台运行，日志写到 logs/）
#   ./start-services.sh --foreground # 前台启动（调试单个服务时用，Ctrl+C 停止）
#
# 和 start-services.ps1 的差异（Linux 特有，值得记住）：
#   · 用 nohup + & 让进程脱离终端，PID 写进 logs/<服务>.pid，便于 stop 脚本精确终止
#   · 依赖地址靠环境变量覆盖：MySQL/Nacos 在 Windows 宿主，Redis/PostgreSQL 在 WSL 本机
#   · 启动前检查端口占用：端口被别的进程占着时直接告警跳过，而不是让它半死不活地起来
# ============================================================================
set -uo pipefail

cd "$(dirname "$0")" || exit 1
ROOT="$(cd ../.. && pwd)"
LOG_DIR="logs"
PID_DIR="logs"
mkdir -p "$LOG_DIR"

FOREGROUND=0
[ "${1:-}" = "--foreground" ] && FOREGROUND=1

# ---------- 1. 载入仓库根的 .env（AI key / JWT / 内部 token / PG 密码等）----------
if [ -f "$ROOT/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "$ROOT/.env"
    set +a
    echo "[OK]   已加载 $ROOT/.env"
else
    echo "[WARN] 未找到 $ROOT/.env，AI/JWT 相关配置会用默认占位值"
fi

# ---------- 2. 依赖地址：默认 MySQL/Nacos 指向 Windows 宿主，Redis/PG 指向 WSL 本机 ----------
if command -v ip >/dev/null 2>&1; then
    WINIP=$(ip route show default | awk '{print $3}')
    export NACOS_ADDR="${NACOS_ADDR:-$WINIP:8848}"
    export MYSQL_HOST="${MYSQL_HOST:-$WINIP}"
    echo "[INFO] Windows 宿主 IP = $WINIP（MySQL/Nacos 指向它）"
fi
export REDIS_HOST="${REDIS_HOST:-localhost}"
export PG_URL="${PG_URL:-jdbc:postgresql://localhost:5432/resume_craft_vector}"
echo "[INFO] NACOS_ADDR=$NACOS_ADDR  MYSQL_HOST=$MYSQL_HOST  REDIS_HOST=$REDIS_HOST"

# ---------- 3. 服务清单：name|port|jar（顺序即启动顺序：业务服务在前，网关最后）----------
SERVICES=(
    "auth-service|8081|auth/target/resumecraft-auth-0.1.0-SNAPSHOT.jar"
    "resume-service|8082|resume/target/resumecraft-resume-0.1.0-SNAPSHOT.jar"
    "job-match-service|8083|job-match/target/resumecraft-job-match-0.1.0-SNAPSHOT.jar"
    "application-service|8084|application/target/resumecraft-application-0.1.0-SNAPSHOT.jar"
    "gateway|8080|gateway/target/resume-craft-gateway-0.1.0-SNAPSHOT.jar"
)

pid_alive() { [ -n "${1:-}" ] && kill -0 "$1" 2>/dev/null; }

# 端口上监听的进程 PID（取第一个）；没占用则输出空
pid_on_port() {
    # $4 是 Local Address:Port，用 $ 锚定端口结尾，避免 :8080 误匹配 :80801
    ss -lntp 2>/dev/null | awk -v p=":$1\$" '$4 ~ p' | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2
}

failed=0
for entry in "${SERVICES[@]}"; do
    IFS='|' read -r name port jar <<< "$entry"
    pidfile="$PID_DIR/$name.pid"

    # 幂等：已经在跑（PID 文件有效）就不重复启动
    if [ -f "$pidfile" ] && pid_alive "$(cat "$pidfile")"; then
        echo "[SKIP] $name 已在运行 (pid=$(cat "$pidfile"))"
        continue
    fi

    # 端口占用检查：不是我们托管的进程就告警跳过，避免"起来了但半死不活"
    other_pid=$(pid_on_port "$port")
    if [ -n "$other_pid" ]; then
        echo "[WARN] 端口 $port 已被 pid=$other_pid 占用，跳过 $name"
        echo "       排查：ss -lntp | grep $port ；确认无用后 kill $other_pid"
        failed=1
        continue
    fi

    if [ ! -f "$jar" ]; then
        echo "[ERROR] 缺少 jar：$jar"
        echo "        先在 apps/server 下执行：mvn -B -DskipTests package"
        failed=1
        continue
    fi

    if [ "$FOREGROUND" = "1" ]; then
        echo "[RUN ] $name 前台启动（端口 $port，Ctrl+C 停止）"
        exec java -jar "$jar"
    fi

    nohup java -jar "$jar" > "$LOG_DIR/$name.out.log" 2>&1 &
    echo $! > "$pidfile"
    echo "[START] $name  pid=$(cat "$pidfile")  端口 $port  日志 logs/$name.out.log"
    sleep 0.5
done

if [ "$FOREGROUND" = "0" ]; then
    echo
    echo "启动命令已执行。用 ./wait-services.sh 等待健康检查（全部 200 才算就绪）："
    echo "    cd $(pwd) && ./wait-services.sh"
fi
exit $failed
