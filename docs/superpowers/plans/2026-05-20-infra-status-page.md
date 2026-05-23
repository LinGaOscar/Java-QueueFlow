# Infra Status Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `/infra.html` 頁面，透過 Spring Actuator 即時顯示 PostgreSQL、Redis、Kafka 三個服務的健康狀態，每 5 秒自動刷新。

**Architecture:** 啟用 Spring Boot Actuator 的 `/actuator/health` 端點（含詳細資訊），前端頁面直接呼叫此 JSON API 並以燈號卡片呈現各服務狀態。不新增自訂後端程式碼，所有 health check 邏輯由 Actuator 自動偵測（DataSource、RedisConnectionFactory、KafkaAdmin 都已在 context 中）。

**Tech Stack:** spring-boot-starter-actuator、原生 HTML/CSS/JS（沿用現有設計系統）

---

## 檔案清單

| 操作 | 路徑 | 說明 |
|------|------|------|
| 修改 | `pom.xml` | 加入 actuator dependency |
| 修改 | `src/main/resources/application.properties` | 開放 health 端點並顯示詳細資訊 |
| 新增 | `src/main/resources/static/infra.html` | 前端狀態監控頁面 |

---

## Task 1：加入 Actuator 並設定 Health 端點

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1：在 pom.xml 加入 actuator dependency**

在 `spring-kafka` dependency 後插入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

- [ ] **Step 2：設定 application.properties**

在檔案末尾加入：

```properties
# ── Actuator ──
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=always
management.endpoint.health.show-components=always
```

`show-details=always` 讓回應包含各 component 的詳細資訊（DB 版本、Kafka topics 等）。

- [ ] **Step 3：建置並啟動確認端點回應**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" mvn spring-boot:run
```

另開 terminal 驗證：

```bash
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

預期回應結構（status 依服務狀態而定）：

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": { "database": "PostgreSQL", "validationQuery": "isValid()" }
    },
    "kafka": {
      "status": "UP",
      "details": { "version": "3.7.0" }
    },
    "ping": { "status": "UP" },
    "redis": { "status": "UP", "details": { "version": "7.x.x" } }
  }
}
```

- [ ] **Step 4：Commit**

```bash
git add pom.xml src/main/resources/application.properties
git commit -m "feat: 加入 Spring Actuator，開放 health 端點含詳細資訊"
```

---

## Task 2：建立 infra.html 狀態監控頁面

**Files:**
- Create: `src/main/resources/static/infra.html`

- [ ] **Step 1：建立 infra.html**

完整建立以下檔案：

```html
<!DOCTYPE html>
<html lang="zh-Hant">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>QueueFlow — Infra Status</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Bebas+Neue&family=IBM+Plex+Mono:wght@300;400;500&family=Cormorant+SC:wght@600&display=swap" rel="stylesheet">
  <style>
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

    :root {
      --bg:        #FFFFFF;
      --surface:   #F9F8F6;
      --surface-2: #F1EEE9;
      --border:    #E2DDD7;
      --border-2:  #C9C3BB;
      --amber:     #B45309;
      --amber-dim: rgba(180,83,9,.3);
      --text:      #1C1917;
      --muted:     #78716C;
      --muted-2:   #57534E;
      --green:     #059669;
      --green-bg:  rgba(5,150,105,.07);
      --red:       #DC2626;
      --red-bg:    rgba(220,38,38,.07);
      --yellow:    #D97706;
      --yellow-bg: rgba(217,119,6,.07);
    }

    html { font-size: 115%; }
    body { min-height: 100vh; background: var(--bg); color: var(--text);
           font-family: 'IBM Plex Mono', monospace; display: flex; flex-direction: column; }

    /* ── Header ── */
    header { display: flex; align-items: center; justify-content: space-between;
             padding: 14px 28px; border-bottom: 1px solid var(--border); }
    .logo { font-family: 'Cormorant SC', serif; font-size: .95rem;
            font-weight: 600; letter-spacing: .3em; color: var(--amber); }
    .logo em { color: var(--muted); font-style: normal; }
    .header-right { display: flex; align-items: center; gap: 16px; }
    .chip { font-size: .44rem; letter-spacing: .2em; padding: 2px 7px;
            border: 1px solid var(--amber-dim); color: var(--amber); text-transform: uppercase; }
    .nav-links { display: flex; gap: 12px; }
    .nav-links a { font-size: .52rem; letter-spacing: .14em; text-transform: uppercase;
                   color: var(--muted); text-decoration: none; transition: color .15s; }
    .nav-links a:hover { color: var(--amber); }

    /* ── Main ── */
    main { flex: 1; padding: 36px 28px; max-width: 900px; margin: 0 auto; width: 100%; }

    .page-title { font-family: 'Bebas Neue', sans-serif; font-size: 2.2rem;
                  letter-spacing: .12em; color: var(--text); margin-bottom: 4px; }
    .page-sub { font-size: .52rem; letter-spacing: .18em; color: var(--muted);
                text-transform: uppercase; margin-bottom: 32px; }

    /* ── Overall badge ── */
    .overall { display: inline-flex; align-items: center; gap: 10px;
               border: 1px solid var(--border); padding: 10px 18px;
               margin-bottom: 32px; background: var(--surface); }
    .overall-label { font-size: .52rem; letter-spacing: .18em;
                     text-transform: uppercase; color: var(--muted-2); }
    .overall-status { font-size: .8rem; font-weight: 500; letter-spacing: .1em; }

    /* ── Cards ── */
    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; }

    .card { border: 1px solid var(--border); padding: 20px 22px;
            background: var(--surface); transition: border-color .2s; }
    .card.up   { border-left: 3px solid var(--green); background: var(--green-bg); }
    .card.down { border-left: 3px solid var(--red);   background: var(--red-bg); }
    .card.unknown { border-left: 3px solid var(--yellow); background: var(--yellow-bg); }

    .card-header { display: flex; align-items: center; justify-content: space-between;
                   margin-bottom: 14px; }
    .card-name { font-size: .62rem; letter-spacing: .22em; text-transform: uppercase;
                 font-weight: 500; color: var(--text); }
    .dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
    .dot.up      { background: var(--green); box-shadow: 0 0 6px var(--green); }
    .dot.down    { background: var(--red);   box-shadow: 0 0 6px var(--red); }
    .dot.unknown { background: var(--yellow); }

    .card-status { font-size: .56rem; letter-spacing: .14em;
                   text-transform: uppercase; font-weight: 500; margin-bottom: 12px; }
    .card-status.up      { color: var(--green); }
    .card-status.down    { color: var(--red); }
    .card-status.unknown { color: var(--yellow); }

    .card-details { border-top: 1px solid var(--border); padding-top: 10px; }
    .detail-row { display: flex; justify-content: space-between; margin-bottom: 4px; }
    .detail-key   { font-size: .46rem; color: var(--muted); letter-spacing: .1em; text-transform: uppercase; }
    .detail-value { font-size: .46rem; color: var(--muted-2); letter-spacing: .06em; max-width: 60%;
                    overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-align: right; }

    /* ── Footer ── */
    .refresh-bar { display: flex; align-items: center; gap: 10px; margin-top: 28px; }
    .refresh-label { font-size: .46rem; letter-spacing: .14em; text-transform: uppercase; color: var(--muted); }
    .refresh-progress { flex: 1; height: 2px; background: var(--border); position: relative; overflow: hidden; }
    .refresh-progress-fill { height: 100%; background: var(--amber); width: 0%;
                              transition: width linear; }
    .last-updated { font-size: .44rem; color: var(--muted); letter-spacing: .08em; }
  </style>
</head>
<body>

<header>
  <div class="logo">Queue<em>Flow</em></div>
  <div class="header-right">
    <div class="nav-links">
      <a href="/index.html">使用者</a>
      <a href="/admin.html">管理後台</a>
    </div>
    <span class="chip">Infra</span>
  </div>
</header>

<main>
  <div class="page-title">INFRA STATUS</div>
  <div class="page-sub">Infrastructure Health Monitor</div>

  <div class="overall">
    <span class="overall-label">Overall</span>
    <span class="overall-status" id="overall-status">—</span>
  </div>

  <div class="cards" id="cards">
    <!-- 動態產生 -->
  </div>

  <div class="refresh-bar">
    <span class="refresh-label">Next refresh</span>
    <div class="refresh-progress"><div class="refresh-progress-fill" id="progress-fill"></div></div>
    <span class="last-updated" id="last-updated">—</span>
  </div>
</main>

<script>
  const INTERVAL = 5000;
  const SERVICES = {
    db:    { label: 'PostgreSQL', detailKeys: { database: 'Engine', validationQuery: 'Validation' } },
    redis: { label: 'Redis',      detailKeys: { version: 'Version' } },
    kafka: { label: 'Kafka',      detailKeys: { version: 'Version' } },
  };

  function statusClass(s) {
    if (!s) return 'unknown';
    return s === 'UP' ? 'up' : 'down';
  }

  function renderCards(components) {
    const container = document.getElementById('cards');
    container.innerHTML = '';
    for (const [key, meta] of Object.entries(SERVICES)) {
      const comp = components?.[key] ?? {};
      const cls  = statusClass(comp.status);
      const details = comp.details ?? {};
      const detailRows = Object.entries(meta.detailKeys)
        .filter(([k]) => details[k] !== undefined)
        .map(([k, label]) =>
          `<div class="detail-row">
            <span class="detail-key">${label}</span>
            <span class="detail-value" title="${details[k]}">${details[k]}</span>
           </div>`)
        .join('');

      container.innerHTML += `
        <div class="card ${cls}">
          <div class="card-header">
            <span class="card-name">${meta.label}</span>
            <span class="dot ${cls}"></span>
          </div>
          <div class="card-status ${cls}">${comp.status ?? 'UNKNOWN'}</div>
          ${detailRows ? `<div class="card-details">${detailRows}</div>` : ''}
        </div>`;
    }
  }

  function renderOverall(status) {
    const el  = document.getElementById('overall-status');
    const cls = statusClass(status);
    const colors = { up: 'var(--green)', down: 'var(--red)', unknown: 'var(--yellow)' };
    el.textContent = status ?? 'UNKNOWN';
    el.style.color = colors[cls] ?? colors.unknown;
  }

  async function fetchHealth() {
    try {
      const res  = await fetch('/actuator/health');
      const data = await res.json();
      renderOverall(data.status);
      renderCards(data.components);
      document.getElementById('last-updated').textContent =
        'Last updated ' + new Date().toLocaleTimeString('zh-TW', { hour12: false });
    } catch (e) {
      renderOverall('DOWN');
      renderCards({});
      document.getElementById('last-updated').textContent = 'Fetch failed';
    }
  }

  // Progress bar animation
  let fillEl = document.getElementById('progress-fill');
  function startProgress() {
    fillEl.style.transition = 'none';
    fillEl.style.width = '0%';
    requestAnimationFrame(() => requestAnimationFrame(() => {
      fillEl.style.transition = `width ${INTERVAL}ms linear`;
      fillEl.style.width = '100%';
    }));
  }

  fetchHealth();
  startProgress();
  setInterval(() => { fetchHealth(); startProgress(); }, INTERVAL);
</script>
</body>
</html>
```

- [ ] **Step 2：重啟應用（靜態資源需重啟生效）**

```bash
# Ctrl+C 停止現有 process，重新啟動
JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" mvn spring-boot:run
```

- [ ] **Step 3：瀏覽器驗證**

開啟 `http://localhost:8080/infra.html`

確認：
- PostgreSQL、Redis、Kafka 三張卡片各自顯示 UP（綠色）
- 頁底進度條每 5 秒重置（代表自動刷新）
- Last updated 時間戳正確更新
- 停止其中一個 Docker container 後，對應卡片在下次刷新後變紅

測試停止 Redis：
```bash
docker stop queueflow-redis
# 等待 5 秒，確認 Redis 卡片變紅
docker start queueflow-redis
# 確認恢復綠色
```

- [ ] **Step 4：Commit**

```bash
git add src/main/resources/static/infra.html
git commit -m "feat: 新增 infra.html 基礎設施狀態監控頁面"
```

---

## Codex Review 後的待修項目

Codex review 在前一次 PR 中指出兩個問題，**本次計劃不修復**（屬於 Kafka audit consumer 的問題，獨立於本次 infra 頁面）：

1. AuditConsumer 的 FK violation 與 idempotency key violation 被同一個 catch 處理
2. schema.sql 的 `CREATE TABLE IF NOT EXISTS` 不會為既有資料庫加 column

這兩個問題可在後續 PR 中修復。
