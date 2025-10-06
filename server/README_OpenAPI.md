# Stocks Server - OpenAPI Integration

## Übersicht

Das Stocks Server Projekt wurde erfolgreich mit OpenAPI/Swagger-Dokumentation erweitert. Die API-Dokumentation ist jetzt über die folgenden Endpunkte verfügbar:

## API-Dokumentations-Endpunkte

### OpenAPI JSON/YAML Definition
- **URL**: `/v3/api-docs`
- **Format**: JSON
- **Beschreibung**: Die vollständige OpenAPI 3.0 Spezifikation der API

### Swagger UI
- **URL**: `/swagger-ui.html`
- **Beschreibung**: Interaktive Web-Oberfläche zur Exploration der API

## Verfügbare API-Endpunkte

Die API bietet folgende Hauptfunktionen:

### Basis-Endpunkte
- `GET /api/` - Service-Status
- `GET /api/onvista/aktien/kennzahlen/{shortUrl}` - OnVista-Berichte

### Aktiendaten
- `GET /api/stock/branch/data` - Branchendaten abrufen
- `GET /api/stock/data` - Aktienrohdaten abrufen
- `GET /api/stock/image` - Aktienchart-Bilder abrufen
- `GET /api/stockItem/prefetch` - Daten vorabladen
- `GET /api/stockItems` - Alle verfügbaren Aktieninformationen

### Benutzereinstellungen
- `GET /api/prefs/{topic}` - Benutzereinstellungen abrufen
- `POST /api/prefs/{topic}` - Benutzereinstellungen setzen

## Konfiguration

Die OpenAPI-Konfiguration erfolgt über:

1. **Maven-Abhängigkeit**: `springdoc-openapi-starter-webmvc-ui`
2. **Konfigurationsklasse**: `OpenApiConfig.java`
3. **Anwendungseinstellungen**: `application.properties`

## Installation und Start

1. Projekt kompilieren:
   ```bash
   mvn clean package
   ```

2. Anwendung starten:
   ```bash
   java -jar target/stocksserver.war
   ```

3. API-Dokumentation aufrufen:
   - OpenAPI JSON: `http://localhost:8080/v3/api-docs`
   - Swagger UI: `http://localhost:8080/swagger-ui.html`

## Neue Dateien und Änderungen (nach Bereinigung)

### Hinzugefügte Dateien:
- `src/main/java/com/straube/jones/config/OpenApiConfig.java` - OpenAPI-Konfiguration

### Geänderte Dateien:
- `pom.xml` - SpringDoc-Abhängigkeit hinzugefügt, veraltete Dependencies entfernt
- `StocksController.java` - OpenAPI-Annotationen hinzugefügt, Code bereinigt

### Entfernte Dependencies:
- `javax.servlet-api` - Bereits von Spring Boot bereitgestellt
- `commons-lang` (v2.2) - Veraltet, commons-lang3 wird verwendet
- `jaxb-api` - Bereits von Spring Boot 3.x bereitgestellt

### Code-Verbesserungen:
- Nicht verwendete Imports entfernt
- Switch-Statements durch if-Statements ersetzt
- Database-Dependencies auf Spring Boot Versionsmanagement umgestellt

## API-Dokumentationsfeatures

- **Vollständige API-Dokumentation** mit Beschreibungen für alle Endpunkte
- **Parameter-Dokumentation** für alle Request-Parameter
- **Response-Schema-Dokumentation** für alle Antworten
- **Interaktive Swagger UI** zum Testen der API
- **Deutsche Beschreibungen** für bessere Benutzerfreundlichkeit

## Verwendung der Swagger UI

1. Navigieren Sie zu `http://localhost:8080/swagger-ui.html`
2. Wählen Sie einen API-Endpunkt aus
3. Klicken Sie auf "Try it out"
4. Geben Sie die erforderlichen Parameter ein
5. Klicken Sie auf "Execute", um die API zu testen

Die OpenAPI-Spezifikation unter `/v3/api-docs` kann auch in andere Tools wie Postman oder API-Clients importiert werden.