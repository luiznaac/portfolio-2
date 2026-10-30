# Why COTAHIST quote refresh returns zero in the running stack

- **Wayfinder ticket**: [Research: why COTAHIST quote refresh returns zero in the running stack](https://github.com/luiznaac/portfolio-2/issues/288)
- **Map**: [Wayfinder map: make an imported B3 export usable end-to-end](https://github.com/luiznaac/portfolio-2/issues/287)
- **Date**: 2026-10-30
- **Environment**: combined image `luiznaac/portfolio:latest` (built 2026-10-30T01:40Z, JDK 22.0.2), container `portfolio-2-app-1`, real MySQL (`portfolio-2-mysql-1`), real B3 host.

## Answer

The failing layer is **B3's `Accept`-header gate**, triggered by the app's shared
`HttpClient` bean. `KtorClientConfig.ktorClient()` installs `ContentNegotiation` with
only `application/json` registered, and Ktor's client ContentNegotiation plugin
therefore sends `Accept: application/json` on **every** outgoing request. For a
`.ZIP` path, B3 answers:

```
HTTP 406 Not Acceptable
content-type: text/html
The resource cannot be displayed because the file extension is not being accepted by your browser.
```

`CotahistGateway.fetch` treats any non-200 as `null`, and `fetchTradingDay()`
swallows even exceptions with `runCatching { ... }.getOrNull()`
(`backend/gateway/src/main/kotlin/dev/agner/portfolio/gateway/cotahist/CotahistGateway.kt:41`).
All seven lookback days return null, so it returns `emptyList()`. `CatalogService.refresh()`
and `QuoteService.refreshEod()` both consume that empty list → `{"catalog":0,"quotes":0}`,
`quote` stays empty, and the allocation values at R$ 0,00.

It is **not** TLS, not DNS, not the container network, not the JDK, and not the parser.

## Evidence

Same container, same B3 host, same CIO engine (`HttpClient {}` auto-discovers
`io.ktor.client.engine.cio.CIOEngine`):

| Client configuration | Request | Result |
| --- | --- | --- |
| `HttpClient {}` (no plugins) | `Accept: */*` | `200 OK`, 591800 bytes, 1442 parsed rows |
| `HttpClient { install(ContentNegotiation) { register(application/json, ...) } }` | `Accept: application/json` | `406 Not Acceptable`, 98-byte HTML body |
| same client, per-request `header(Accept, "*/*")` | `Accept: */*` | `200 OK`, 591800 bytes |
| the real `CotahistGateway` + ContentNegotiation client | `Accept: application/json` | `0` rows returned |

Header gate, reproduced with `curl` against the same URL:

```
Accept: application/json -> 406, 98 bytes, text/html
Accept: application/zip  -> 406, 98 bytes, text/html
Accept: */*              -> 200, 591800 bytes
(no Accept header)       -> 200, 591800 bytes
```

Cross-checks that cleared the other suspects:

- `curl`, `java.net.http.HttpClient`, and a plain `HttpClient(CIO)` in the container all
  return `200 / 591800 bytes`; the ZIP magic is `50 4b 03 04` and the app's own
  `CotahistParser` parses `1442` spot rows from it.
- JVM DNS in the container resolves IPv4 first (`172.64.144.61, 104.18.43.195`, then AAAA);
  IPv6 egress is absent but unused by the resolved order.
- The container clock is UTC, so `yesterday` is `2026-10-29`; `COTAHIST_D29102026.ZIP`
  exists (last-modified `2026-10-29T23:40:35Z`).
- Two immediate back-to-back `Accept: */*` requests both return 200 in ~0.14 s, so a
  rate limit is not a factor for the catalog-then-quotes double fetch.

The "host JVM returns 1442 rows" observation in the ticket is consistent with a plain
CIO client; it does not exercise the app's shared, ContentNegotiation-configured client.

## Fix shape (decision, not implemented here)

- **Not** an engine/UA/timeout change: the engine and TLS path are healthy.
- Set `Accept: */*` explicitly on the COTAHIST GET (per-request header), so the global
  JSON negotiation cannot hijack the ZIP request. This is the minimal fix.
- Replace the silent `runCatching { ... }.getOrNull()` with explicit failure surfacing:
  record the non-200 status and body, so a future 406/403 is diagnosable from the app
  instead of collapsing to zero. The existing 7-day lookback is correct and stays.
- No fallback host is needed for this failure mode.

## Acceptance statement

Once the request carries `Accept: */*`, the same stack fetches the same file
(200, 591800 bytes, 1442 rows). Both `CatalogService.refresh()` and
`QuoteService.refreshEod()` then upsert non-zero, so `catalog` and `quotes` become
non-zero in this exact combined-image stack, and the allocation stops reading R$ 0,00
for valued tickers.

## Reproduction

```sh
curl -s -o /dev/null -w '%{http_code} %{size_download}\n' \
  -H 'Accept: application/json' \
  https://bvmf.bmfbovespa.com.br/InstDados/SerHist/COTAHIST_D29102026.ZIP
# 406 98

curl -s -o /dev/null -w '%{http_code} %{size_download}\n' \
  -H 'Accept: */*' \
  https://bvmf.bmfbovespa.com.br/InstDados/SerHist/COTAHIST_D29102026.ZIP
# 200 591800
```
