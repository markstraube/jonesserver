# Accounting Service

Der Accounting Service erweitert das Stock Trading Backend um Funktionen für Budget-Tracking, Portfolio-Bewertung und Performance-Historie.

## API Dokumentation

Base URL: `/api/accounting`

### Budget

*   **GET /budget**
    *   Liefert das aktuelle Gesamtvermögen (Budget, Portfolio, Cash).
    *   Response: `BudgetDto`
*   **POST /budget**
    *   Setzt das Budget neu. Cash wird angepasst (`Cash = Budget - Portfolio`).
    *   Request: `BudgetDto` (nur `budget` erforderlich)

### Portfolio

*   **GET /portfolio**
    *   Berechnet den aktuellen Portfolio-Wert basierend auf aktiven Positionen und aktuellen Preisen.
    *   Response: `PortfolioValueDto`
*   **POST /portfolio**
    *   Setzt den Portfolio-Wert manuell (Accounting-Override, ändert keine Positionen).
    *   Request: `PortfolioValueDto`
*   **POST /portfolio/buy**
    *   Führt einen Aktienkauf durch. Prüft Cash, erstellt Position, aktualisiert Cash/Budget.
    *   Request: `TransactionDto`
    *   Response: `TransactionDto` (mit TransactionId)
*   **POST /portfolio/sell**
    *   Führt einen Aktienverkauf durch. Aktualisiert/Schließt Position, gutschrift Cash.
    *   Request: `TransactionDto` (PositionId, Quantity, Price)

### Cash

*   **GET /cash**
    *   Liefert den verfügbaren Cash-Bestand.
    *   Response: `BudgetDto` (Feld `cash`)

### Performance

*   **GET /performance**
    *   Historische Performance-Daten (Tages-Endstände).
    *   Params: `from`, `to` (DayCounter Werte)
*   **GET /performance/week**
    *   Detaillierte Performance der letzten 5 Tage.

## Architektur & Setup

Dieses Modul nutzt eine direkte JDBC Verbindung (`tPerformance`, `tPortfolio`) und integriert sich in die bestehende User-Verwaltung.

### Requirements
*   Java 21
*   MariaDB (Tables: `tPerformance`, `tPortfolio` vorhanden)

### Scheduler
Der `PortfolioTrackingScheduler` läuft alle 30 Sekunden:
1.  Lädt aktive Positionen aller User.
2.  Holt aktuelle Preise via `PriceTickerService`.
3.  Berechnet Portfolio-Wert und aktualisiert das Budget (Unrealized Gains/Losses).
4.  Speichert Snapshot in `tPerformance`.
5.  Markiert 22:00 Uhr Snapshots mit `cKeepMe=1`.
6.  Bereinigt wöchentlich alte Daten (Montag 06:00).
