# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案簡介

QueueFlow 是一套以 Java 21 + Spring Boot 建置的高併發線上候補排隊系統，目標支援演唱會、限量商品、餐廳候位等流量集中湧入場景。**第一階段為單體架構**，第二階段預留 Kafka 事件化擴充空間。

## 技術棧

- **語言/框架：** Java 21、Spring Boot、Spring Web、Spring Data JPA、Spring WebSocket
- **資料層：** PostgreSQL（持久化）、Redis（排隊結構、限流、分散式鎖）
- **建置工具：** Maven

## 本地開發環境

啟動依賴服務（PostgreSQL 5432、Redis 6379）：

```bash
docker-compose up -d
```

連線預設值（對應 `application.properties` 設定）：

| 服務 | 主機 | DB/Port | 帳號/密碼 |
|------|------|---------|-----------|
| PostgreSQL | localhost:5432 | queueflow | queueflow / queueflow |
| Redis | localhost:6379 | — | 無密碼 |

## 常用指令

Java 21 在 macOS 上由 Homebrew 安裝為 keg-only，執行指令須帶 `JAVA_HOME`：

```bash
# 建置（跳過測試）
JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" mvn clean package -DskipTests

# 執行測試
JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" mvn test

# 執行單一測試類別
JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" mvn test -Dtest=QueueServiceTest

# 啟動應用（需先啟動 Docker 依賴）
JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" mvn spring-boot:run
```

啟動後前端頁面：
- 使用者：`http://localhost:8080/index.html`
- 管理後台：`http://localhost:8080/admin.html`

## 套件結構

```
com.example.queueflow
├─ api            # Controller，對外 REST 入口
├─ application    # 用例服務（QueueService、EventService）
├─ domain         # 核心業務模型（QueueEntry、QueueEvent）及狀態常數
├─ infrastructure # JPA Repository、RedisQueueStore
├─ realtime       # WebSocket 推播配置與訊息發送
├─ batch          # 逾時失效定時任務
└─ common         # 統一回應格式（ApiResponse）、例外處理
```

## 核心資料模型

### PostgreSQL

| 表格 | 用途 |
|------|------|
| `queue_event` | 活動資料（name, status, capacity, open_time, close_time） |
| `queue_entry` | 排隊記錄（event_id, user_id, status, joined_at, admitted_at, expired_at） |
| `queue_audit_log` | 操作歷程（entry_id, action, payload） |

### QueueEntry 狀態流轉

```
WAITING → ADMITTED（管理員放行）
WAITING → EXPIRED（活動關閉後由排程清除）
WAITING → CANCELLED（使用者主動取消）
```

### Redis Key 設計

| Key | 用途 |
|-----|------|
| `queue:{eventId}` | Sorted Set，score = 原子序列，儲存排隊順序 |
| `queue:seq:{eventId}` | INCR 原子序列，保證同毫秒並發加入時 FIFO 順序 |
| `queue:user:{eventId}:{userId}` | 防止重複加入 |
| `ratelimit:join:{userId}` | 限流（SET NX EX 5s） |
| `admit:lock:{eventId}` | 放行分散式鎖 |

## API 設計

### 使用者端
- `POST /api/events/{eventId}/queue/join?userId=` — 加入候補
- `GET /api/events/{eventId}/queue/me?userId=` — 查詢順位
- `DELETE /api/events/{eventId}/queue/me?userId=` — 取消候補

### 管理端
- `GET /api/admin/events` — 列出所有活動（頁面重整後恢復清單用）
- `POST /api/admin/events` — 建立活動
- `POST /api/admin/events/{eventId}/open` — 開啟候補
- `POST /api/admin/events/{eventId}/close` — 關閉候補
- `POST /api/admin/events/{eventId}/admit` — 分批放行（body: `{"count": N}`，N 必須 > 0）
- `GET /api/admin/events/{eventId}/queue` — 查詢清單

## WebSocket 主題

- `/topic/events/{eventId}/queue` — 全局順位更新（payload: `{queueSize}`）
- `/topic/users/{userId}/queue-status` — 個人狀態通知（payload: `{status}`）

## 高併發關鍵設計

1. **Redis 作為主排隊結構**：用 Sorted Set 維護順序，避免直接打 DB。
2. **FIFO 保證**：以 `INCR queue:seq:{eventId}` 取原子遞增值作為 score，而非 `currentTimeMillis`，防止同毫秒並發加入時的字典序亂序。
3. **防重複入列**：Lua script 原子執行「檢查 user key → SETEX → ZADD」，確保不重複入列。
4. **分散式鎖放行**：`admit:lock:{eventId}` 防止管理端重複放行同一批次。
5. **限流保護**：`ratelimit:join:{userId}` 用 SET NX EX 限制短時間內重複請求。
6. **逾時語意**：排程只清除**活動已關閉（CLOSED）**後殘留的 WAITING 記錄，不影響正在等候的使用者。

## 重要實作細節

- **Schema 管理**：`spring.jpa.hibernate.ddl-auto=none`，Hibernate 不管理 schema；改由 `src/main/resources/schema.sql` 在每次啟動時由 Spring 自動執行（`spring.sql.init.mode=always` + `spring.jpa.defer-datasource-initialization=true`）。表格使用 `CREATE TABLE IF NOT EXISTS`，重複啟動安全。
- **錯誤處理模式**：`AppException` 提供靜態工廠方法（`notFound` / `badRequest` / `conflict` / `tooManyRequests`）直接對應 HTTP 狀態碼；`GlobalExceptionHandler` 統一轉換為 `ApiResponse<Void>`。新增業務錯誤只需呼叫對應工廠方法，無需修改 Handler。
- **`@Transactional` 範圍差異**：`EventService` 整個 class 加 `@Transactional`；`QueueService` 僅 `joinQueue`/`cancelQueue` 方法層級加，`getPosition` 用 `readOnly = true`，以明確控制讀寫分離。
- **逾時排程**：`QueueExpiryScheduler` 以 `fixedDelay = 60_000`（每分鐘）掃描並失效已關閉活動的殘留 WAITING 記錄。`application.properties` 中的 `queue.expiry.minutes=30` 目前尚未被 Scheduler 引用（預留供未來參數化使用）。
- **`QueueService.joinQueue`** 使用 `@Transactional` 同步寫入 DB，確保後續 cancel/admit 能立即查到記錄（Phase 2 可改為 Kafka 事件驅動非同步寫入）。
- **多次入列查詢**：取消或放行後 Redis user key 被刪除，同一使用者可再次入列。Repository 使用 `findByEventIdAndUserIdAndStatus(..., WAITING)` 而非通用查詢，避免多筆記錄時拋出 non-unique-result exception。
- **`/me` 歷史查詢**：Redis 無記錄時改用 `findFirstByEventIdAndUserIdOrderByJoinedAtDesc` 回傳最新終態。
- **Jackson**：設定 `write-dates-as-timestamps=false`，`LocalDateTime` 序列化為 ISO 8601 字串，前端直接用 `new Date(str)` 解析。
- **靜態資源熱更新**：`mvn spring-boot:run` 從 `target/classes/static` 服務 HTML/JS，編輯 `src/main/resources/static/` 後**必須重啟伺服器**才能生效（瀏覽器強制重新整理不夠）。
- **前端 inline handler 命名禁忌**：`onclick="fn()"` 的作用域鏈包含 `with(document)`，因此函式名稱不可與瀏覽器 DOM API 重名（例如 `createEvent` 會被解析為 `document.createEvent()` 而非 window 上的自訂函式）。

## 第二階段擴充（預留）

第二階段將 Kafka 用於以下事件的非同步消費，不應在第一階段主流程中引入依賴：
- `queue-created`, `queue-admitted`, `queue-cancelled`, `queue-expired`
- 拆出：通知服務、審計服務、分析服務
