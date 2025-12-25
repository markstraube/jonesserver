# Swing Trading API

REST API für das Swing-Trading-Backend.

## Startanleitung

1.  Projekt bauen: `mvn clean install`
2.  Starten: `mvn spring-boot:run`
3.  Swagger UI öffnen: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## Endpunkte

### Watchlist
`GET /api/swing-trades`

**Parameter:**
- `status`: GREEN, YELLOW, RED
- `minCrv`: z.B. 2.0
- `maxRsi`: z.B. 70

**Beispiel:**
`GET /api/swing-trades?status=GREEN&minCrv=2`

### Detailansicht
`GET /api/swing-trades/{symbol}`

**Beispiel:**
`GET /api/swing-trades/AAPL`

### Health Check
`GET /api/swing-trades/health`

## Mock-Daten
Die API verwendet aktuell Mock-Services für Marktdaten, Events und Unternehmensnamen.
