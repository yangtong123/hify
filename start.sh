#!/usr/bin/env bash
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_DIR="$PROJECT_DIR/.pids"
BACKEND_PID_FILE="$PID_DIR/backend.pid"
FRONTEND_PID_FILE="$PID_DIR/frontend.pid"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-123456}"

mkdir -p "$PID_DIR"

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

cleanup() {
    echo ""
    echo -e "${YELLOW}[STOP]${NC} 停止服务..."
    for f in "$BACKEND_PID_FILE" "$FRONTEND_PID_FILE"; do
        if [[ -f "$f" ]]; then
            pid=$(cat "$f")
            if kill -0 "$pid" 2>/dev/null; then
                kill "$pid" 2>/dev/null
                wait "$pid" 2>/dev/null || true
            fi
            rm -f "$f"
        fi
    done
    rm -rf "$PID_DIR"
    echo -e "${GREEN}      已停止${NC}"
}

trap cleanup EXIT INT TERM

echo ""
echo "══════════════════════════════════════════"
echo "  Hify 一键启动"
echo "══════════════════════════════════════════"
echo ""

# ── 1. MySQL ──────────────────────────────────────────
echo -e "${YELLOW}[1/5]${NC} 检查 MySQL..."

if command -v mysqladmin &>/dev/null; then
    if mysqladmin ping -h127.0.0.1 -uroot -p"$MYSQL_PASSWORD" --silent 2>/dev/null; then
        echo -e "${GREEN}       MySQL 可用 (localhost)${NC}"
    else
        echo -e "${YELLOW}       MySQL 连接失败，尝试无密码连接...${NC}"
        if mysqladmin ping -h127.0.0.1 -uroot --silent 2>/dev/null; then
            echo -e "${GREEN}       MySQL 可用 (localhost, 无密码)${NC}"
        else
            error "MySQL 连接失败，请确保 MySQL 服务已启动 (brew services start mysql 或 systemctl start mysqld)"
        fi
    fi
else
    error "未找到 mysqladmin 命令，请安装 MySQL 客户端 (brew install mysql-client 或 apt install mysql-client)"
fi

# ── 2. Redis ──────────────────────────────────────────
echo -e "${YELLOW}[2/5]${NC} 检查 Redis..."

if command -v redis-cli &>/dev/null; then
    if redis-cli ping 2>/dev/null | grep -q PONG; then
        echo -e "${GREEN}       Redis 可用 (localhost)${NC}"
    else
        error "Redis 连接失败，请确保 Redis 服务已启动 (brew services start redis 或 systemctl start redis)"
    fi
else
    error "未找到 redis-cli 命令，请安装 Redis (brew install redis 或 apt install redis)"
fi

# ── 3. 构建后端 ──────────────────────────────────────
echo -e "${YELLOW}[3/5]${NC} 构建后端..."
cd "$PROJECT_DIR"

mvn package -pl hify-app -am -DskipTests -q 2>&1 | tail -5
if [[ ${PIPESTATUS[0]} -ne 0 ]]; then
    error "后端构建失败，请查看上方错误信息"
fi
echo -e "${GREEN}      后端构建完成${NC}"

# ── 4. 启动后端 ──────────────────────────────────────
echo -e "${YELLOW}[4/5]${NC} 启动后端..."

JAR_FILE=$(ls "$PROJECT_DIR"/hify-app/target/hify-app-*.jar 2>/dev/null | head -1)
if [[ -z "$JAR_FILE" ]]; then
    error "找不到后端 JAR 包"
fi

java -jar "$JAR_FILE" &
echo $! > "$BACKEND_PID_FILE"
echo -e "${GREEN}      后端已启动 (PID: $(cat "$BACKEND_PID_FILE"))${NC}"

# ── 5. 等待健康检查 ──────────────────────────────────
echo -e "${YELLOW}[5/5]${NC} 等待后端健康检查..."
echo -n "      "

HEALTH_URL="http://localhost:8080/api/v1/health"
MAX_WAIT=60

for i in $(seq 1 "$MAX_WAIT"); do
    if curl -s --noproxy localhost "$HEALTH_URL" 2>/dev/null | grep -q 'success'; then
        echo ""
        echo -e "${GREEN}      后端就绪 ✓${NC}"
        break
    fi
    if [[ $i -eq "$MAX_WAIT" ]]; then
        echo ""
        error "后端健康检查超时 (${MAX_WAIT}s)，请查看后端日志"
    fi
    echo -n "."
    sleep 1
done

# ── 6. 启动前端 ──────────────────────────────────────
echo ""
echo -e "${YELLOW}[前端]${NC} 启动 Vite 开发服务器..."
echo -e "══════════════════════════════════════════${NC}"
echo ""

cd "$PROJECT_DIR/hify-web"
npm run dev &
echo $! > "$FRONTEND_PID_FILE"

wait
