# Market Data Scraper

Intraday market data aggregator for active trading. Scrapes quote, options, short interest and news data from Yahoo Finance, Finviz, Barchart and MarketChameleon.

## Requirements

- Java 21+
- Maven 3.8+

## Build & Run

```bash
# Build
./mvnw clean package

# Run
./mvnw spring-boot:run

# Or run the jar directly
java --enable-preview -jar target/market-data-scraper-1.0.0-SNAPSHOT.jar
```

## Swagger UI

After starting the application, open: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## API Endpoints

### Quote — current price & volume

```bash
curl http://localhost:8080/api/v1/quote/AAPL
curl http://localhost:8080/api/v1/quote/TSLA
```

### Options — Put/Call ratio, IV Rank, unusual activity

```bash
curl http://localhost:8080/api/v1/options/AAPL
```

### Short Interest — Short Float %, Days-to-Cover

```bash
curl http://localhost:8080/api/v1/short/GME
```

### News — recent headlines with sentiment

```bash
curl "http://localhost:8080/api/v1/news/AAPL?limit=5"
```

### Full Snapshot — all data sources aggregated in parallel

```bash
curl http://localhost:8080/api/v1/snapshot/AAPL
```

### Batch Snapshot — multiple tickers at once

```bash
curl "http://localhost:8080/api/v1/snapshot/batch?tickers=AAPL,MSFT,TSLA,GME"
```

---

## Data Sources

| Data                | Source              | Cache TTL  |
|---------------------|---------------------|-----------|
| Quote / Price       | Yahoo Finance API   | 60 s      |
| Options / IV        | Barchart + MarketChameleon | 120 s |
| Short Interest      | Finviz              | 3600 s    |
| News                | Yahoo Finance RSS   | 180 s     |

---

## Configuration (`src/main/resources/application.properties`)

| Property                         | Default | Description                      |
|----------------------------------|---------|----------------------------------|
| `server.port`                    | 8080    | HTTP port                        |
| `scraper.connect-timeout-ms`     | 3000    | TCP connect timeout (ms)         |
| `scraper.read-timeout-ms`        | 5000    | HTTP read timeout (ms)           |
| `scraper.rate-limit-delay-min-ms`| 500     | Min random delay between requests|
| `scraper.rate-limit-delay-max-ms`| 1500    | Max random delay between requests|
| `cache.quote.ttl-seconds`        | 60      | Quote cache TTL                  |
| `cache.options.ttl-seconds`      | 120     | Options cache TTL                |
| `cache.short.ttl-seconds`        | 3600    | Short interest cache TTL         |
| `cache.news.ttl-seconds`         | 180     | News cache TTL                   |

---

## Architecture Notes

- **Virtual Threads** (Java 21 Loom) are used for parallel scraping — no async callback hell
- **Graceful degradation**: if a scraper fails, the response still returns HTTP 200 with `dataAvailable: false` and a `sourceError` message
- **Caffeine caches** per data category with configurable TTLs
- **Rate limiting**: random delay (500–1500 ms) + rotating User-Agent headers per request
- **Snapshot endpoint** fetches all four data sources in parallel using `CompletableFuture.allOf()`
