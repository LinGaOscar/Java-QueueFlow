# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案簡介

QueueFlow 是一套以 Java 21 + Spring Boot 建置的高併發線上候補排隊系統，目標支援演唱會、限量商品、餐廳候位等流量集中湧入場景。**第一階段為單體架構**，第二階段預留 Kafka 事件化擴充空間。

> **專案狀態**：目前為 greenfield 起始狀態，尚未建立 `src/` 目錄；CLAUDE.md 反映完整的設計規格，作為實作依據。

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

```bash
# 建置（跳過測試）
mvn clean package -DskipTests

# 執行測試
mvn test

# 執行單一測試類別
mvn test -Dtest=QueueServiceTest

# 啟動應用（需先啟動 Docker 依賴）
mvn spring-boot:run
```

## 套件結構

```
com.example.queueflow
├─ api            # Controller，對外 REST 入口
├─ application    # 用例服務（QueueService、EventService）
├─ domain         # 核心業務模型（QueueEntry、QueueEvent）及狀態常數
├─ infrastructure # JPA Repository、Redis 操作、排程
├─ realtime       # WebSocket 推播配置與訊息發送
├─ batch          # 逾時失效定時任務
└─ common         # 統一回應格式、例外處理
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
WAITING → EXPIRED（逾時未處理）
WAITING → CANCELLED（使用者主動取消）
```

### Redis Key 設計

| Key | 用途 |
|-----|------|
| `queue:{eventId}` | Sorted Set，儲存排隊順序 |
| `queue:user:{eventId}:{userId}` | 防止重複加入 |
| `ratelimit:join:{userId}` | 限流 |
| `admit:lock:{eventId}` | 放行分散式鎖 |

## API 設計

### 使用者端
- `POST /api/events/{eventId}/queue/join` — 加入候補
- `GET /api/events/{eventId}/queue/me` — 查詢順位
- `DELETE /api/events/{eventId}/queue/me` — 取消候補

### 管理端
- `POST /api/admin/events` — 建立活動
- `POST /api/admin/events/{eventId}/open` — 開啟候補
- `POST /api/admin/events/{eventId}/close` — 關閉候補
- `POST /api/admin/events/{eventId}/admit` — 分批放行
- `GET /api/admin/events/{eventId}/queue` — 查詢清單

## WebSocket 主題

- `/topic/events/{eventId}/queue` — 全局順位更新
- `/topic/users/{userId}/queue-status` — 個人狀態通知（放行、逾時）

## 高併發關鍵設計

1. **Redis 作為主排隊結構**：用 Sorted Set 維護順序，避免直接打 DB。
2. **防重複入列**：`queue:user:{eventId}:{userId}` 設值後才寫入排隊結構，兩步需原子化。
3. **分散式鎖放行**：`admit:lock:{eventId}` 防止管理端重複放行同一批次。
4. **限流保護**：`ratelimit:join:{userId}` 限制短時間內重複請求。
5. **DB 寫入時序**：Redis 入列成功後再非同步寫入 PostgreSQL，降低延遲。

## 第二階段擴充（預留）

第二階段將 Kafka 用於以下事件的非同步消費，不應在第一階段主流程中引入依賴：
- `queue-created`, `queue-admitted`, `queue-cancelled`, `queue-expired`
- 拆出：通知服務、審計服務、分析服務
