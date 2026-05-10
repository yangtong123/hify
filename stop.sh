#!/usr/bin/env bash
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_DIR="$PROJECT_DIR/.pids"
GRACE_PERIOD=10

stop_process() {
    local name=$1
    local pid_file=$2

    if [[ ! -f "$pid_file" ]]; then
        echo -e "${YELLOW}[SKIP]${NC} $name — PID 文件不存在，跳过"
        return
    fi

    pid=$(cat "$pid_file")

    if ! kill -0 "$pid" 2>/dev/null; then
        echo -e "${YELLOW}[SKIP]${NC} $name — 进程 $pid 已不存在"
        rm -f "$pid_file"
        return
    fi

    echo -ne "${YELLOW}[STOP]${NC} $name (PID: $pid) — 发送 SIGTERM..."

    kill "$pid" 2>/dev/null || true

    # 轮询等待进程退出
    for i in $(seq 1 "$GRACE_PERIOD"); do
        if ! kill -0 "$pid" 2>/dev/null; then
            echo -e " ${GREEN}已退出 ✓${NC}"
            rm -f "$pid_file"
            return
        fi
        sleep 1
    done

    # 超时，SIGKILL
    echo -ne " ${RED}超时，发送 SIGKILL...${NC}"
    kill -9 "$pid" 2>/dev/null || true
    sleep 1

    if ! kill -0 "$pid" 2>/dev/null; then
        echo -e " ${GREEN}已强制终止 ✓${NC}"
    else
        echo -e " ${RED}无法终止${NC}"
    fi
    rm -f "$pid_file"
}

echo ""
echo "══════════════════════════════════════════"
echo "  Hify 停止"
echo "══════════════════════════════════════════"
echo ""

stop_process "后端" "$PID_DIR/backend.pid"
stop_process "前端" "$PID_DIR/frontend.pid"

rm -rf "$PID_DIR"
echo ""
echo -e "${GREEN}完成${NC}"
echo ""
