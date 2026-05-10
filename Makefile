.PHONY: start stop restart build clean package

APP_NAME     := hify
VERSION      := $(shell grep -m1 '<version>' pom.xml | head -1 | sed -E 's/.*<version>(.*)<\/version>.*/\1/')
PACKAGE_DIR  := $(APP_NAME)-$(VERSION)
JAR_FILE     := hify-app/target/hify-app-$(VERSION).jar

start:
	@./start.sh

stop:
	@./stop.sh

restart: stop start

# ── 构建 ─────────────────────────────────────────────

build: build-backend build-frontend

build-backend:
	@echo "=== 构建后端 ==="
	mvn package -pl hify-app -am -DskipTests -q
	@echo "  后端构建完成"

build-frontend:
	@echo "=== 构建前端 ==="
	cd hify-web && npm run build
	@echo "  前端构建完成"

# ── 清理 ─────────────────────────────────────────────

clean:
	@echo "=== 清理构建产物 ==="
	mvn clean -q
	rm -rf hify-web/dist
	rm -rf .pids
	@echo "  清理完成"

# ── 打包 ─────────────────────────────────────────────

package: clean build
	@echo "=== 打包 $(PACKAGE_DIR).tar.gz ==="
	rm -rf $(PACKAGE_DIR) $(PACKAGE_DIR).tar.gz
	mkdir -p $(PACKAGE_DIR)
	cp $(JAR_FILE) $(PACKAGE_DIR)/
	cp -r hify-web/dist $(PACKAGE_DIR)/
	cp docker-compose.yml $(PACKAGE_DIR)/ 2>/dev/null || true
	cp start.sh stop.sh $(PACKAGE_DIR)/ 2>/dev/null || true
	@echo "  #!/usr/bin/env bash"  > $(PACKAGE_DIR)/run.sh
	@echo 'cd "$$(dirname "$$0")"' >> $(PACKAGE_DIR)/run.sh
	@echo 'java -jar hify-app-$(VERSION).jar &' >> $(PACKAGE_DIR)/run.sh
	@echo 'echo "Hify started at http://localhost:8080"' >> $(PACKAGE_DIR)/run.sh
	chmod +x $(PACKAGE_DIR)/run.sh $(PACKAGE_DIR)/start.sh $(PACKAGE_DIR)/stop.sh 2>/dev/null || true
	tar czf $(PACKAGE_DIR).tar.gz $(PACKAGE_DIR)
	rm -rf $(PACKAGE_DIR)
	@echo "  $(PACKAGE_DIR).tar.gz 已生成"
