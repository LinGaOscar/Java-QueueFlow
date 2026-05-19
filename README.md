# QueueFlow

高併發線上候補排隊系統。以 Java 21 + Spring Boot 建置，採用 Redis Sorted Set 作為主排隊結構，支援演唱會、限量商品、餐廳候位等流量集中湧入場景。

---

## 系統架構

```
瀏覽器（SockJS/STOMP）
        │  HTTP REST + WebSocket
        ▼
┌─────────────────────────────────────┐
│          Spring Boot App            │
│  ┌──────────┐   ┌────────────────┐  │
│  │ REST API │   │ WebSocket Push │  │
│  └────┬─────┘   └───────┬────────┘  │
│       │                 │           │
│  ┌────▼─────────────────▼────────┐  │
│  │       Application Services    │  │
│  │  QueueService / EventService  │  │
│  └────┬──────────────────────────┘  │
│       │                            │
│  ┌────▼──────┐   ┌───────────────┐  │
│  │   Redis   │   │  PostgreSQL   │  │
│  │ (排隊結構) │   │  (持久化)     │  │
│  └───────────┘   └───────────────┘  │
└─────────────────────────────────────┘
```

**流程說明**

1. 使用者加入候補 → API 限流檢查 → Lua script 原子寫入 Redis → 同步寫入 PostgreSQL
2. 管理員放行 → 取分散式鎖 → 從 Redis 取前 N 名 → 更新 DB → WebSocket 通知
3. 逾時排程（每分鐘）→ 只清除活動已關閉後殘留的 WAITING 記錄

---

## 環境需求

| 工具 | 版本 |
|------|------|
| Java | 21 |
| Maven | 3.9+ |
| Docker | 20+ |
| Docker Compose | V2（`docker compose` 指令） |

---

## 快速啟動

### 1. 啟動依賴服務

```bash
docker compose up -d
```

啟動後：PostgreSQL 監聽 `5432`，Redis 監聽 `6379`。

```bash
# 確認容器狀態
docker compose ps
```

### 2. 建置並啟動應用

macOS（Homebrew 安裝 Java 21 為 keg-only）：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" \
mvn spring-boot:run
```

Linux / 已設定 `JAVA_HOME` 的環境：

```bash
mvn spring-boot:run
```

### 3. 開啟頁面

| 頁面 | URL |
|------|-----|
| 使用者候補頁 | http://localhost:8080/index.html |
| 管理後台 | http://localhost:8080/admin.html |

---

## Docker 設定說明

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16
    container_name: queueflow-postgres
    environment:
      POSTGRES_DB:       queueflow
      POSTGRES_USER:     queueflow
      POSTGRES_PASSWORD: queueflow
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data   # 資料持久化

  redis:
    image: redis:7
    container_name: queueflow-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data                         # AOF/RDB 持久化
```

資料表結構由 `src/main/resources/schema.sql` 在應用啟動時自動建立（`CREATE TABLE IF NOT EXISTS`），不需手動執行 SQL。

常用維運指令：

```bash
# 停止並保留資料
docker compose stop

# 停止並刪除容器（資料 volume 保留）
docker compose down

# 完整清除（含資料 volume）
docker compose down -v

# 查看 PostgreSQL logs
docker compose logs postgres

# 進入 PostgreSQL CLI
docker compose exec postgres psql -U queueflow -d queueflow

# 進入 Redis CLI
docker compose exec redis redis-cli
```

---

## 應用設定

`src/main/resources/application.properties`

| 設定 | 預設值 | 說明 |
|------|--------|------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/queueflow` | DB 連線 |
| `spring.data.redis.host` | `localhost` | Redis 主機 |
| `spring.data.redis.port` | `6379` | Redis 埠 |
| `queue.expiry.minutes` | `30` | 保留設定（目前逾時改由活動關閉觸發，此值暫無作用） |

---

## API 一覽

### 使用者端

```
POST   /api/events/{eventId}/queue/join?userId=   加入候補
GET    /api/events/{eventId}/queue/me?userId=     查詢順位
DELETE /api/events/{eventId}/queue/me?userId=     取消候補
```

### 管理端

```
POST /api/admin/events                       建立活動
POST /api/admin/events/{eventId}/open        開放候補
POST /api/admin/events/{eventId}/close       關閉候補
POST /api/admin/events/{eventId}/admit       分批放行（body: {"count": N}）
GET  /api/admin/events/{eventId}/queue       查詢候補清單
```

---

## 技術棧

- **後端**：Java 21、Spring Boot 3.3、Spring Data JPA、Spring WebSocket（STOMP）
- **資料層**：PostgreSQL 16、Redis 7
- **前端**：Vanilla JS + SockJS + STOMP.js（靜態頁面由 Spring Boot 托管）
- **建置**：Maven 3.9
