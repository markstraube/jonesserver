# Price Ticker API

## Overview
The Price Ticker API retrieves current stock price information by scraping Yahoo Finance HTML pages. It provides real-time market prices along with extended hours trading data (pre-market and after-market) when available.

## Endpoint

```
GET /api/price-ticker?isin={ISIN}
```

## Parameters

| Parameter | Type   | Required | Description                                      |
|-----------|--------|----------|--------------------------------------------------|
| `isin`    | string | Yes      | The ISIN of the stock (e.g., US0378331005)      |

## Response Format

### Success Response (200 OK)

```json
{
  "isin": "US0378331005",
  "symbolYahoo": "AAPL",
  "currency": "USD",
  "prices": [
    {
      "type": "REGULAR",
      "price": 195.89,
      "changeAbsolute": 2.35,
      "changePercent": 1.21,
      "timestamp": "2025-12-22T16:00:00Z",
      "qualifier": "At close"
    },
    {
      "type": "AFTER_MARKET",
      "price": 196.10,
      "changeAbsolute": 0.21,
      "changePercent": 0.11,
      "timestamp": "2025-12-22T20:00:00Z",
      "qualifier": "After hours"
    }
  ],
  "source": "Yahoo Finance",
  "retrievedAt": "2025-12-22T20:15:30.123Z"
}
```

### Error Response

```json
{
  "error": {
    "code": "ISIN_NOT_FOUND",
    "message": "ISIN not resolvable to Yahoo symbol",
    "details": "ISIN not found: US1234567890",
    "timestamp": "2025-12-22T20:15:30.123Z"
  }
}
```

## HTTP Status Codes

| Status Code | Description                                                 |
|-------------|-------------------------------------------------------------|
| 200         | Prices successfully retrieved (even partial data)           |
| 400         | Missing or invalid ISIN parameter                           |
| 404         | ISIN not resolvable to Yahoo symbol                         |
| 502         | Yahoo Finance page not reachable                            |
| 503         | HTML structure changed / price block not found              |
| 500         | Unexpected parsing or runtime error                         |

## Price Types

| Type         | Description                                                  |
|--------------|--------------------------------------------------------------|
| `REGULAR`    | Price from the main exchange during regular trading hours    |
| `PRE_MARKET` | Price from pre-market trading                                |
| `AFTER_MARKET` | Price from after-hours trading                             |

## Timestamp Qualifiers

| Qualifier      | Meaning                                                    |
|----------------|-----------------------------------------------------------|
| `As of`        | Current timestamp during open market hours                |
| `At close`     | Timestamp of the last update after market close           |
| `Pre-Market`   | Timestamp from a different currently open exchange        |
| `After hours`  | Timestamp from after-hours trading session                |

## Examples

### Example 1: Get Apple Inc. stock price

**Request:**
```bash
curl "http://localhost:8080/api/price-ticker?isin=US0378331005"
```

**Response:**
```json
{
  "isin": "US0378331005",
  "symbolYahoo": "AAPL",
  "currency": "USD",
  "prices": [
    {
      "type": "REGULAR",
      "price": 195.89,
      "changeAbsolute": 2.35,
      "changePercent": 1.21,
      "timestamp": "2025-12-22T16:00:00Z",
      "qualifier": "At close"
    }
  ],
  "source": "Yahoo Finance",
  "retrievedAt": "2025-12-22T20:15:30.123Z"
}
```

### Example 2: Invalid ISIN

**Request:**
```bash
curl "http://localhost:8080/api/price-ticker?isin=INVALID123"
```

**Response (404):**
```json
{
  "error": {
    "code": "ISIN_NOT_FOUND",
    "message": "ISIN not resolvable to Yahoo symbol",
    "details": "ISIN not found: INVALID123",
    "timestamp": "2025-12-22T20:15:30.123Z"
  }
}
```

### Example 3: Missing ISIN parameter

**Request:**
```bash
curl "http://localhost:8080/api/price-ticker"
```

**Response (400):**
```json
{
  "error": {
    "code": "INVALID_ISIN",
    "message": "Missing or invalid ISIN parameter",
    "details": "The 'isin' query parameter is required and cannot be empty",
    "timestamp": "2025-12-22T20:15:30.123Z"
  }
}
```

## Implementation Notes

1. **ISIN Resolution**: The API uses the existing StockFundamentals service to resolve the ISIN to a Yahoo Finance symbol (SYMBOL.YAHOO field).

2. **HTML Scraping**: Price information is extracted from Yahoo Finance HTML pages using JSoup. The implementation is designed to be robust against minor HTML structure changes.

3. **Multiple Price Blocks**: During extended hours or when markets are closed, Yahoo Finance may display:
   - Regular market price (last closing price)
   - Pre-market price (if available)
   - After-market price (if available)

4. **Timestamp Parsing**: Timestamps are extracted from the Yahoo Finance page and converted to ISO-8601 format. The qualifier indicates the context of each timestamp.

5. **Currency Detection**: Currency is automatically detected from the Yahoo Finance page, with USD as the default fallback.

## Prerequisites

Before using this API, ensure:

1. The stock's ISIN exists in the StockFundamentals database
2. The StockFundamentals entry contains a valid `SYMBOL.YAHOO` field
3. The server has internet access to reach Yahoo Finance

## Testing

To test the API with known ISINs:

```bash
# Apple Inc.
curl "http://localhost:8080/api/price-ticker?isin=US0378331005"

# Microsoft Corporation
curl "http://localhost:8080/api/price-ticker?isin=US5949181045"

# Alphabet Inc. (Google)
curl "http://localhost:8080/api/price-ticker?isin=US02079K3059"
```

## OpenAPI/Swagger Documentation

Once the server is running, visit:
```
http://localhost:8080/swagger-ui.html
```

Look for the "Price Ticker API" section to test the endpoint interactively.
