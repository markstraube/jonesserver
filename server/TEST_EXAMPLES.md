# Price Ticker API - Quick Test Examples

## Prerequisites
1. Start the Spring Boot application
2. Ensure the server is running on http://localhost:8080
3. Make sure you have curl or a similar HTTP client

## Test Cases

### Test 1: Valid ISIN (Apple Inc.)
```bash
curl -i "http://localhost:8080/api/price-ticker?isin=US0378331005"
```
**Expected:** 200 OK with price data

---

### Test 2: Valid ISIN (Microsoft)
```bash
curl -i "http://localhost:8080/api/price-ticker?isin=US5949181045"
```
**Expected:** 200 OK with price data

---

### Test 3: Valid ISIN (Alphabet/Google)
```bash
curl -i "http://localhost:8080/api/price-ticker?isin=US02079K3059"
```
**Expected:** 200 OK with price data

---

### Test 4: Missing ISIN Parameter
```bash
curl -i "http://localhost:8080/api/price-ticker"
```
**Expected:** 400 Bad Request with error response

---

### Test 5: Empty ISIN Parameter
```bash
curl -i "http://localhost:8080/api/price-ticker?isin="
```
**Expected:** 400 Bad Request with error response

---

### Test 6: Invalid ISIN
```bash
curl -i "http://localhost:8080/api/price-ticker?isin=INVALID123456"
```
**Expected:** 404 Not Found with error response

---

### Test 7: ISIN without Yahoo Symbol
```bash
curl -i "http://localhost:8080/api/price-ticker?isin=XX1234567890"
```
**Expected:** 404 Not Found (assuming this ISIN doesn't exist in fundamentals)

---

## Using PowerShell (Windows)

### Test with Invoke-RestMethod
```powershell
# Test valid ISIN
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/price-ticker?isin=US0378331005" -Method Get
$response | ConvertTo-Json -Depth 10

# Test with error handling
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/price-ticker?isin=INVALID" -Method Get
    $response | ConvertTo-Json -Depth 10
} catch {
    Write-Host "Status Code:" $_.Exception.Response.StatusCode.value__
    Write-Host "Error:" $_.ErrorDetails.Message
}
```

---

## Using Postman or Browser

### Interactive Testing
1. Open Swagger UI: http://localhost:8080/swagger-ui.html
2. Look for "Price Ticker API" section
3. Click on GET /api/price-ticker
4. Click "Try it out"
5. Enter an ISIN (e.g., US0378331005)
6. Click "Execute"
7. View response below

---

## Verify Response Structure

### Success Response Structure Check
```bash
curl -s "http://localhost:8080/api/price-ticker?isin=US0378331005" | jq .
```

Should return:
```json
{
  "isin": "US0378331005",
  "symbolYahoo": "AAPL",
  "currency": "USD",
  "prices": [
    {
      "type": "REGULAR",
      "price": <number>,
      "changeAbsolute": <number>,
      "changePercent": <number>,
      "timestamp": "<ISO-8601>",
      "qualifier": "<string>"
    }
  ],
  "source": "Yahoo Finance",
  "retrievedAt": "<ISO-8601>"
}
```

### Error Response Structure Check
```bash
curl -s "http://localhost:8080/api/price-ticker?isin=INVALID" | jq .
```

Should return:
```json
{
  "error": {
    "code": "<string>",
    "message": "<string>",
    "details": "<string>",
    "timestamp": "<ISO-8601>"
  }
}
```

---

## Performance Testing

### Check Response Time
```bash
time curl -s "http://localhost:8080/api/price-ticker?isin=US0378331005" > /dev/null
```

### Multiple Requests
```bash
for i in {1..5}; do
  echo "Request $i:"
  curl -w "@-" -o /dev/null -s "http://localhost:8080/api/price-ticker?isin=US0378331005" <<'EOF'
    time_total:  %{time_total}s\n
EOF
done
```

---

## Debugging

### Enable Verbose Output
```bash
curl -v "http://localhost:8080/api/price-ticker?isin=US0378331005"
```

### Check Headers Only
```bash
curl -I "http://localhost:8080/api/price-ticker?isin=US0378331005"
```

### Save Response to File
```bash
curl -o response.json "http://localhost:8080/api/price-ticker?isin=US0378331005"
cat response.json | jq .
```

---

## Common Issues & Troubleshooting

### Issue: 404 - ISIN Not Found
**Cause:** ISIN doesn't exist in StockFundamentals database
**Solution:** Add the stock to fundamentals database first
```bash
# Check if fundamentals exist
curl "http://localhost:8080/api/fundamentals/US0378331005"
```

### Issue: 502 - Bad Gateway
**Cause:** Cannot reach Yahoo Finance
**Solution:** 
- Check internet connectivity
- Verify Yahoo Finance is accessible: https://finance.yahoo.com
- Check firewall settings

### Issue: 503 - Service Unavailable
**Cause:** Yahoo Finance HTML structure changed
**Solution:** 
- Update PriceTickerService selectors
- Check Yahoo Finance page manually
- Review application logs

### Issue: Slow Response
**Cause:** Network latency to Yahoo Finance
**Solution:**
- Normal for first request
- Consider implementing caching
- Check network bandwidth

---

## Integration with Fundamentals API

### Step 1: Check if ISIN has Yahoo Symbol
```bash
curl "http://localhost:8080/api/fundamentals/US0378331005" | jq '.["SYMBOL.YAHOO"]'
```

### Step 2: If Yahoo Symbol exists, get price
```bash
curl "http://localhost:8080/api/price-ticker?isin=US0378331005"
```

---

## Notes

- Price data is real-time from Yahoo Finance
- Extended hours data (pre-market/after-market) may not always be available
- Response time depends on Yahoo Finance server response
- No caching implemented - each request fetches fresh data
- Timestamps are in ISO-8601 format
- Currency is auto-detected, defaults to USD

---

## Sample Test ISINs

| Company          | ISIN          | Symbol |
|------------------|---------------|--------|
| Apple Inc.       | US0378331005  | AAPL   |
| Microsoft Corp.  | US5949181045  | MSFT   |
| Alphabet Inc.    | US02079K3059  | GOOGL  |
| Amazon.com Inc.  | US0231351067  | AMZN   |
| Tesla Inc.       | US88160R1014  | TSLA   |

**Note:** These ISINs must exist in your StockFundamentals database with valid SYMBOL.YAHOO values.
