#!/usr/bin/env bash
# ============================================================================
# ResumeCraft 微服务健康等待脚本（Linux / WSL）
#
# 用法：
#   ./wait-services.sh            # 默认最多等 180s
#   ./wait-services.sh 300        # 自定义超时
#
# 退出码：0 = 5 个服务全部健康；1 = 超时仍有服务不健康（CI/脚本可直接判断）
#
# 为什么用 /actuator/health 的 **HTTP 200** 判定，而不是看端口通不通：
#   端口能连上只说明进程活着；健康检查会把依赖（Redis/MySQL/PG/Nacos）算进去，
#   依赖不健康时会返回 503 —— 只看端口会误判"启动成功"。
# ============================================================================
set -uo pipefail

cd "$(dirname "$0")" || exit 1
TIMEOUT="${1:-180}"

names=(gateway auth-service resume-service job-match-service application-service)
ports=(8080 8081 8082 8083 8084)
status=()
for i in "${!names[@]}"; do status[$i]=0; done

deadline=$(( $(date +%s) + TIMEOUT ))
echo "[WAIT] 轮询 /actuator/health，最多 ${TIMEOUT}s（200 才算健康，503 = 依赖有问题）"

while :; do
    remaining=0
    for i in "${!names[@]}"; do
        [ "${status[$i]}" = "1" ] && continue
        code=$(curl -s -o /dev/null -m 3 -w "%{http_code}" "http://localhost:${ports[$i]}/actuator/health" 2>/dev/null || echo 000)
        if [ "$code" = "200" ]; then
            status[$i]=1
            echo "[OK]   ${names[$i]} :${ports[$i]} 健康"
        else
            remaining=$((remaining + 1))
        fi
    done

    [ "$remaining" = "0" ] && { echo "[DONE] 全部服务健康 ✔"; exit 0; }
    [ "$(date +%s)" -ge "$deadline" ] && break
    sleep 2
done

echo
echo "[FAIL] 超时（${TIMEOUT}s），以下服务还没健康："
for i in "${!names[@]}"; do
    [ "${status[$i]}" = "1" ] && continue
    code=$(curl -s -o /dev/null -m 3 -w "%{http_code}" "http://localhost:${ports[$i]}/actuator/health" 2>/dev/null || echo 000)
    echo "  - ${names[$i]} :${ports[$i]}  HTTP=$code"
    [ "$code" = "503" ] && echo "      → 进程活着但依赖不健康，看详情：curl -s http://localhost:${ports[$i]}/actuator/health"
    [ "$code" = "000" ] && echo "      → 端口没人监听，看日志：tail -n 40 logs/${names[$i]}.out.log"
done
exit 1
