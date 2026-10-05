# Stock Intelligence — V1 Backend

Spring Boot modular monolith for personal stock intelligence: watchlists of Indian equities (NSE/BSE),
scheduled AI-powered analysis reports, and independent critical-alert monitoring. REST APIs only (no frontend).

## Server (Azure — no local dependency)

1. Portal `stockintel-api-new > Configuration > Application settings`: import `azure-appsettings.example.json` and fill `<...>` values (Neon + Gmail App-Password + API keys).
2. Portal `General settings`: `Stack Java 21`, `Always On: On (Basic B1+)`, `Health check: /actuator/health`, `Scale out Manual: 1` (V1 scheduler is single-instance, all 4 crons pinned `Asia/Kolkata`).
3. `git push main` triggers `.github/workflows/main_stockintel-api-new.yml` (`mvn clean install` → JAR deploy). No `docker compose` / `.env` / `localhost` needed.
4. Verify server-only: `https://stockintel-api-new-b7bafzh6hhb9edhq.centralindia-01.azurewebsites.net/actuator/health → UP`, `POST .../api/admin/test-mail → accepted:true` + Gmail received, `Log stream` shows `Schedule check: / EOD digest: mailed`.
5. Daily mails: `EOD 16:00 IST Mon-Fri` always mailed, `roundup 13:00 IST` if MEDIUM, `alert-monitor` every 4h, `report-check` hourly (`REPORT_CHECK_CRON / ALERT_MONITOR_CRON / EOD_DIGEST_CRON / MEDIUM_ROUNDUP_CRON`).

## Quick start (local dev alternative)

1. Start infrastructure: `docker compose up -d` (PostgreSQL on 5432, MailHog SMTP on 1025 / UI on 8025).
2. Copy `.env.example` to `.env` and fill in API keys (or export the vars).
3. Run: `mvn spring-boot:run` (Java 21 required). Flyway migrates the schema automatically.
4. Swagger UI: `http://localhost:8080/swagger-ui.html`

## Typical V1 flow

```bash
# 1. Add stocks
curl -X POST localhost:8080/api/stocks -H 'Content-Type: application/json' \
  -d '{"symbol":"RELIANCE","companyName":"Reliance Industries","exchange":"NSE"}'

# 2. Create watchlist + add stocks
curl -X POST localhost:8080/api/watchlists -H 'Content-Type: application/json' -d '{"name":"India Core 10"}'
curl -X POST localhost:8080/api/watchlists/1/stocks -H 'Content-Type: application/json' -d '{"stockId":1}'

# 3. Configure schedule (DAILY | WEEKLY | MONTHLY)
curl -X PUT localhost:8080/api/watchlists/1/schedule -H 'Content-Type: application/json' \
  -d '{"frequency":"DAILY","timezone":"Asia/Kolkata","active":true}'

# 4. Trigger analysis manually (also runs hourly via scheduler when due)
curl -X POST localhost:8080/api/watchlists/1/analyze

# 5. Read reports + alerts
curl localhost:8080/api/reports
curl localhost:8080/api/reports/1
curl localhost:8080/api/alerts
curl -X PUT localhost:8080/api/alerts/1/read
```

## Configuration

All secrets via environment (see `.env.example`). Provider choice:

| Purpose      | `APP_*` var               | Options                          |
|--------------|---------------------------|----------------------------------|
| Market data  | `APP_MARKET_DATA_PROVIDER`| `alphavantage` (default), `twelvedata` |
| News         | `APP_NEWS_PROVIDER`       | `newsapi` (default), `gnews`, `newsdata` |
| AI           | `APP_AI_PROVIDER`         | `openai` = any OpenAI-compatible `AI_BASE_URL` |

Notes:

- Free market-data tiers have daily caps and are **not tick-level real-time**; the app never claims otherwise.
- Fundamentals and quotes degrade gracefully; missing data yields `INSUFFICIENT_DATA` with low confidence.
- AI output is validated as strict JSON before saving; failures fall back to rule-based analysis.
- Without an `AI_API_KEY`, the app still runs end-to-end (rule-based fallback + alerts + reports).
- NSE/BSE symbols map to vendor symbols (`RELIANCE`+`NSE` → `RELIANCE.NSE` for Alpha Vantage);
  symbols already containing a suffix pass through. Verify vendor coverage for your symbols.
- Alert monitor runs every 4h by default (`app.scheduling.alert-monitor-cron`), independent of report frequency.
- News providers (free tiers, 2026): `newsapi` 100 req/day, 24h delay, dev-only; `gnews`
  100 req/day, 10 art/req, 12h delay, `country=in`, dev-only; `newsdata` (recommended
  for NSE/BSE) 200 req/day, 10 art/req, 12h delay, `country=in`, commercial use allowed.
  Set `APP_NEWS_PROVIDER=newsdata` + `NEWSDATA_API_KEY`. Free `newsdata` has no archive search,
  so the 14-day window is filtered client-side and `news-max-articles` is capped at one page (~10).
- AI free tiers: Groq `openai/gpt-oss-120b` is 8k TPM (~6 stocks/min at ~1.2k tokens/call —
  expect 429s on big watchlists). Mitigations built in: prompt capped to `AI_MAX_NEWS_FOR_AI=3`
  x 120 chars, `AI_MAX_TOKENS=500`, 429s retried 4x with backoff, watchlist calls paced by
  `AI_MIN_INTERVAL_MS=12000`. Note: gpt-oss models lack JSON mode, so the app skips
  `response_format` for them (override with `AI_JSON_MODE=false` for other models).
  Higher-burst swap with no code change:
  `AI_MODEL=meta-llama/llama-4-scout-17b-16e-instruct` (30k TPM) or Gemini free
  (`AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai/`,
  `AI_MODEL=gemini-2.0-flash`). 429s still degrade gracefully to rule-based fallback.

## Tests

`mvn test` — unit tests (rules, AI validation, alert dedup, REST contract) + JPA persistence tests (H2).
