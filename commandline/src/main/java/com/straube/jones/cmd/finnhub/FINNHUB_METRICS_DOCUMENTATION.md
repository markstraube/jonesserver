# FinnHub Metrics Dokumentation

Diese Dokumentation beschreibt die Bedeutung aller Felder, die von der FinnHub API in den Fundamental-Daten zurückgegeben werden.

## Datenstruktur

Die JSON-Datei enthält folgende Hauptbereiche:
- **symbol**: Aktiensymbol (z.B. "CG")
- **metrics**: Konsolidierte Kennzahlen aus allen drei API-Aufrufen
- **rawData**: Rohdaten von FinnHub (profile, metrics, quote)

---

## Profile-Daten (Firmeninformationen)

| Feld | Beschreibung | Beispiel |
|------|--------------|----------|
| `name` | Vollständiger Firmenname | "Carlyle Group Inc" |
| `ticker` | Börsenkürzel | "CG" |
| `country` | Land der Firmenzentrale | "US" |
| `currency` | Handelswährung | "USD" |
| `exchange` | Börsenplatz | "NASDAQ NMS - GLOBAL MARKET" |
| `finnhubIndustry` | Branche/Industrie | "Financial Services" |
| `weburl` | Firmen-Website | "https://www.carlyle.com" |
| `phone` | Telefonnummer | "12027295626" |
| `ipo` | Datum des Börsengangs | "2012-05-03" |
| `marketCapitalization` | Marktkapitalisierung in Millionen | 19426.13 (= ~19.4 Mrd.) |
| `shareOutstanding` | Anzahl ausstehender Aktien in Millionen | 361.7 (= ~362 Mio.) |
| `logo` | URL zum Firmenlogo | "https://..." |

---

## Quote-Daten (Aktuelle Kursdaten)

| Feld | Beschreibung | Beispiel |
|------|--------------|----------|
| `c` | Current price - Aktueller Kurs | 55.02 |
| `h` | High - Tageshöchstkurs | 55.28 |
| `l` | Low - Tagestiefstkurs | 54.285 |
| `o` | Open - Eröffnungskurs | 54.49 |
| `pc` | Previous close - Schlusskurs vom Vortag | 53.9 |
| `d` | Change - Absolute Änderung | 1.12 |
| `dp` | Change percent - Prozentuale Änderung | 2.0779 (= +2.08%) |
| `t` | Timestamp - Unix-Zeitstempel | 1762974188 |

---

## Metrics (Finanzkennzahlen)

### Bewertungskennzahlen (Valuation)

| Feld | Beschreibung | Berechnung |
|------|--------------|------------|
| **P/E Ratios (Kurs-Gewinn-Verhältnis)** |
| `peNormalizedAnnual` | P/E-Ratio (normalisiert, jährlich) | Aktienkurs ÷ Gewinn pro Aktie (bereinigt) |
| `peBasicExclExtraTTM` | P/E-Ratio (TTM, ohne Sonderposten) | Kurs ÷ EPS (ohne außerordentliche Posten) |
| `peTTM` | P/E-Ratio (TTM) | Trailing Twelve Months |
| `peAnnual` | P/E-Ratio (jährlich) | Aktueller Kurs ÷ Jahresgewinn pro Aktie |
| `forwardPE` | Forward P/E | Kurs ÷ erwarteter zukünftiger Gewinn |
| `pegTTM` | PEG-Ratio | P/E ÷ Gewinnwachstumsrate (zeigt ob P/E gerechtfertigt) |
| **Preis-Buch-Verhältnis** |
| `pbAnnual` | Price-to-Book (jährlich) | Aktienkurs ÷ Buchwert pro Aktie |
| `pbQuarterly` | Price-to-Book (quartalsweise) | Kurs ÷ Buchwert (letztes Quartal) |
| `pb` | Price-to-Book (aktuell) | Aktueller Wert |
| `ptbvAnnual` | **Price-to-Tangible-Book-Value** (jährlich) | Kurs ÷ materieller Buchwert pro Aktie |
| `ptbvQuarterly` | Price-to-Tangible-Book-Value (quartalsweise) | Wie oben, aber aktuelleres Quartal |
| **Andere Bewertungen** |
| `psAnnual` | Price-to-Sales (jährlich) | Marktkapitalisierung ÷ Jahresumsatz |
| `psTTM` | Price-to-Sales (TTM) | Market Cap ÷ Umsatz (letzte 12 Monate) |
| `pcfShareTTM` | Price-to-Cash-Flow (TTM) | Kurs ÷ Cashflow pro Aktie |
| `pfcfShareAnnual` | Price-to-Free-Cash-Flow (jährlich) | Kurs ÷ freier Cashflow pro Aktie |
| `pfcfShareTTM` | Price-to-Free-Cash-Flow (TTM) | Wie oben für letzte 12 Monate |

### Rentabilitätskennzahlen (Profitability)

| Feld | Beschreibung | Formel |
|------|--------------|--------|
| **Margen** |
| `netProfitMarginAnnual` | Nettogewinnmarge (jährlich) | Nettogewinn ÷ Umsatz × 100 |
| `netProfitMarginTTM` | Nettogewinnmarge (TTM) | Wie oben für letzte 12 Monate |
| `grossMarginAnnual` | Bruttomarge (jährlich) | (Umsatz - Herstellkosten) ÷ Umsatz × 100 |
| `grossMarginTTM` | Bruttomarge (TTM) | Wie oben für TTM |
| `operatingMarginAnnual` | Operative Marge (jährlich) | Operatives Ergebnis ÷ Umsatz × 100 |
| `operatingMarginTTM` | Operative Marge (TTM) | Wie oben für TTM |
| `pretaxMarginAnnual` | Vorsteuergewinnmarge (jährlich) | Gewinn vor Steuern ÷ Umsatz × 100 |
| `pretaxMarginTTM` | Vorsteuergewinnmarge (TTM) | Wie oben für TTM |
| **Returns (Renditen)** |
| `roeRfy` | Return on Equity (RFY) | Nettogewinn ÷ Eigenkapital × 100 |
| `roeTTM` | Return on Equity (TTM) | Wie oben für TTM |
| `roaRfy` | Return on Assets (RFY) | Nettogewinn ÷ Gesamtvermögen × 100 |
| `roaTTM` | Return on Assets (TTM) | Wie oben für TTM |
| `roiAnnual` | Return on Investment (jährlich) | Gewinn ÷ Investition × 100 |
| `roiTTM` | Return on Investment (TTM) | Wie oben für TTM |

### Gewinnkennzahlen (Earnings)

| Feld | Beschreibung | Details |
|------|--------------|---------|
| `epsAnnual` | Earnings per Share (jährlich) | Gewinn pro Aktie |
| `epsTTM` | EPS (TTM) | Letzte 12 Monate |
| `epsBasicExclExtraItemsAnnual` | Basis-EPS ohne Sonderposten (jährlich) | Bereinigter Gewinn pro Aktie |
| `epsBasicExclExtraItemsTTM` | Basis-EPS ohne Sonderposten (TTM) | Wie oben für TTM |
| `epsNormalizedAnnual` | Normalisierter EPS (jährlich) | EPS bereinigt um Ausreißer |
| `epsInclExtraItemsAnnual` | EPS inkl. Sonderposten (jährlich) | Alle Gewinne eingeschlossen |
| `epsExclExtraItemsAnnual` | EPS ohne Sonderposten (jährlich) | Operative Gewinne |
| `epsGrowthTTMYoy` | EPS-Wachstum (YoY, TTM) | Wachstum gegenüber Vorjahr (%) |
| `epsGrowthQuarterlyYoy` | EPS-Wachstum (quartalsweise, YoY) | Quartalswachstum (%) |
| `epsGrowth3Y` | EPS-Wachstum (3 Jahre) | Durchschnittliches Wachstum 3 Jahre |
| `epsGrowth5Y` | EPS-Wachstum (5 Jahre) | Durchschnittliches Wachstum 5 Jahre |

### Dividendenkennzahlen (Dividends)

| Feld | Beschreibung | Berechnung |
|------|--------------|------------|
| `dividendYieldIndicatedAnnual` | Dividendenrendite (indiziert, jährlich) | Dividende ÷ Aktienkurs × 100 |
| `currentDividendYieldTTM` | Aktuelle Dividendenrendite (TTM) | Wie oben für TTM |
| `dividendPerShareAnnual` | Dividende pro Aktie (jährlich) | Gesamtdividende ÷ Aktienanzahl |
| `dividendPerShareTTM` | Dividende pro Aktie (TTM) | Wie oben für TTM |
| `dividendIndicatedAnnual` | Angekündigte Dividende (jährlich) | Erwartete Jahresdividende |
| `payoutRatioAnnual` | Ausschüttungsquote (jährlich) | Dividende ÷ Gewinn × 100 |
| `payoutRatioTTM` | Ausschüttungsquote (TTM) | Wie oben für TTM |
| `dividendGrowthRate5Y` | Dividendenwachstum (5 Jahre) | Durchschnittliches Wachstum (%) |

### Liquiditätskennzahlen (Liquidity)

| Feld | Beschreibung | Formel |
|------|--------------|--------|
| `currentRatioAnnual` | Current Ratio (jährlich) | Umlaufvermögen ÷ kurzfristige Verbindlichkeiten |
| `currentRatioQuarterly` | Current Ratio (quartalsweise) | Wie oben, aktuelles Quartal |
| `quickRatioAnnual` | Quick Ratio (jährlich) | (Umlaufvermögen - Vorräte) ÷ kurzfristige Verbindlichkeiten |
| `quickRatioQuarterly` | Quick Ratio (quartalsweise) | Wie oben, aktuelles Quartal |

**Interpretation:**
- **> 2.0**: Sehr gute Liquidität
- **1.0 - 2.0**: Gesunde Liquidität
- **< 1.0**: Mögliche Liquiditätsprobleme

### Verschuldungskennzahlen (Leverage)

| Feld | Beschreibung | Formel |
|------|--------------|--------|
| `totalDebt/totalEquityAnnual` | Verschuldungsgrad (jährlich) | Gesamtschulden ÷ Eigenkapital |
| `totalDebt/totalEquityQuarterly` | Verschuldungsgrad (quartalsweise) | Wie oben, aktuelles Quartal |
| `longTermDebt/equityAnnual` | Langfristige Verschuldung (jährlich) | Langfristige Schulden ÷ Eigenkapital |
| `longTermDebt/equityQuarterly` | Langfristige Verschuldung (quartalsweise) | Wie oben, aktuelles Quartal |
| `netInterestCoverageAnnual` | Zinsdeckungsgrad (jährlich) | EBIT ÷ Zinsaufwand |
| `netInterestCoverageTTM` | Zinsdeckungsgrad (TTM) | Wie oben für TTM |

**Interpretation Debt-to-Equity:**
- **< 0.5**: Niedrige Verschuldung
- **0.5 - 1.0**: Moderate Verschuldung
- **> 1.0**: Hohe Verschuldung

### Umsatzkennzahlen (Revenue)

| Feld | Beschreibung | Details |
|------|--------------|---------|
| `revenuePerShareAnnual` | Umsatz pro Aktie (jährlich) | Gesamtumsatz ÷ Aktienanzahl |
| `revenuePerShareTTM` | Umsatz pro Aktie (TTM) | Wie oben für TTM |
| `revenueGrowthTTMYoy` | Umsatzwachstum (YoY, TTM) | Wachstum gegenüber Vorjahr (%) |
| `revenueGrowthQuarterlyYoy` | Umsatzwachstum (quartalsweise, YoY) | Quartalswachstum (%) |
| `revenueGrowth3Y` | Umsatzwachstum (3 Jahre) | Durchschnitt 3 Jahre |
| `revenueGrowth5Y` | Umsatzwachstum (5 Jahre) | Durchschnitt 5 Jahre |
| `revenueShareGrowth5Y` | Umsatzanteilswachstum (5 Jahre) | Wachstum pro Aktie (%) |
| `revenueEmployeeAnnual` | Umsatz pro Mitarbeiter (jährlich) | Gesamtumsatz ÷ Mitarbeiterzahl |
| `revenueEmployeeTTM` | Umsatz pro Mitarbeiter (TTM) | Wie oben für TTM |

### Cashflow-Kennzahlen (Cash Flow)

| Feld | Beschreibung | Details |
|------|--------------|---------|
| `cashFlowPerShareAnnual` | Cashflow pro Aktie (jährlich) | Operativer Cashflow ÷ Aktienanzahl |
| `cashFlowPerShareTTM` | Cashflow pro Aktie (TTM) | Wie oben für TTM |
| `cashFlowPerShareQuarterly` | Cashflow pro Aktie (quartalsweise) | Aktuelles Quartal |
| `cashPerSharePerShareAnnual` | Cash pro Aktie (jährlich) | Bargeldbestand ÷ Aktienanzahl |
| `cashPerSharePerShareQuarterly` | Cash pro Aktie (quartalsweise) | Aktuelles Quartal |

### Buchwert-Kennzahlen (Book Value)

| Feld | Beschreibung | Formel |
|------|--------------|--------|
| `bookValuePerShareAnnual` | Buchwert pro Aktie (jährlich) | Eigenkapital ÷ Aktienanzahl |
| `bookValuePerShareQuarterly` | Buchwert pro Aktie (quartalsweise) | Wie oben, aktuelles Quartal |
| `tangibleBookValuePerShareAnnual` | **Materieller Buchwert pro Aktie (jährlich)** | (Eigenkapital - immaterielle Vermögenswerte) ÷ Aktienanzahl |
| `tangibleBookValuePerShareQuarterly` | Materieller Buchwert pro Aktie (quartalsweise) | Wie oben, aktuelles Quartal |
| `bookValueShareGrowth5Y` | Buchwert-Wachstum pro Aktie (5 Jahre) | Durchschnittliches Wachstum (%) |

**Hinweis zu "Tangible Book Value":**
Der materielle Buchwert schließt immaterielle Vermögenswerte wie Patente, Marken, Goodwill aus und zeigt den "echten" physischen Wert des Unternehmens.

### 52-Wochen-Kennzahlen (52 Week Range)

| Feld | Beschreibung | Details |
|------|--------------|---------|
| `52WeekHigh` | 52-Wochen-Hoch | Höchster Kurs der letzten 52 Wochen |
| `52WeekLow` | 52-Wochen-Tief | Niedrigster Kurs der letzten 52 Wochen |
| `52WeekHighDate` | Datum 52-Wochen-Hoch | Wann wurde das Hoch erreicht |
| `52WeekLowDate` | Datum 52-Wochen-Tief | Wann wurde das Tief erreicht |
| `52WeekPriceReturnDaily` | 52-Wochen-Rendite | Kursänderung über 52 Wochen (%) |

### Risikokennzahlen (Risk)

| Feld | Beschreibung | Interpretation |
|------|--------------|----------------|
| `beta` | Beta-Faktor | Volatilität relativ zum Markt |
|  | Beta < 1 | Aktie schwankt weniger als Markt |
|  | Beta = 1 | Aktie schwankt wie Markt |
|  | Beta > 1 | Aktie schwankt mehr als Markt |
| `3MonthADReturnStd` | 3-Monats-Standardabweichung | Volatilität der Renditen |

### Performance-Kennzahlen (Performance)

| Feld | Beschreibung | Zeitraum |
|------|--------------|----------|
| `5DayPriceReturnDaily` | 5-Tages-Rendite | Kursänderung 5 Tage (%) |
| `13WeekPriceReturnDaily` | 13-Wochen-Rendite | ~3 Monate |
| `26WeekPriceReturnDaily` | 26-Wochen-Rendite | ~6 Monate |
| `52WeekPriceReturnDaily` | 52-Wochen-Rendite | 1 Jahr |
| `monthToDatePriceReturnDaily` | Month-to-Date-Rendite | Seit Monatsanfang |
| `yearToDatePriceReturnDaily` | Year-to-Date-Rendite | Seit Jahresanfang |

### Relative Performance zu S&P 500

| Feld | Beschreibung | Interpretation |
|------|--------------|----------------|
| `priceRelativeToS&P5004Week` | Relative Performance (4 Wochen) | Outperformance/Underperformance vs. S&P 500 (%) |
| `priceRelativeToS&P50013Week` | Relative Performance (13 Wochen) | Wie oben für ~3 Monate |
| `priceRelativeToS&P50026Week` | Relative Performance (26 Wochen) | Wie oben für ~6 Monate |
| `priceRelativeToS&P50052Week` | Relative Performance (52 Wochen) | Wie oben für 1 Jahr |
| `priceRelativeToS&P500Ytd` | Relative Performance (YTD) | Seit Jahresanfang |

**Interpretation:**
- **Positiver Wert**: Aktie besser als S&P 500
- **Negativer Wert**: Aktie schlechter als S&P 500

### Sonstige Kennzahlen

| Feld | Beschreibung | Details |
|------|--------------|---------|
| `assetTurnoverAnnual` | Asset Turnover (jährlich) | Umsatz ÷ Gesamtvermögen |
| `assetTurnoverTTM` | Asset Turnover (TTM) | Effizienz der Vermögensnutzung |
| `receivablesTurnoverAnnual` | Forderungsumschlag (jährlich) | Umsatz ÷ Forderungen |
| `receivablesTurnoverTTM` | Forderungsumschlag (TTM) | Wie schnell Forderungen eingetrieben werden |
| `netIncomeEmployeeAnnual` | Gewinn pro Mitarbeiter (jährlich) | Nettogewinn ÷ Mitarbeiterzahl |
| `netIncomeEmployeeTTM` | Gewinn pro Mitarbeiter (TTM) | Wie oben für TTM |

### Handelsvolumen

| Feld | Beschreibung | Einheit |
|------|--------------|---------|
| `3MonthAverageTradingVolume` | Durchschnittliches Handelsvolumen (3 Monate) | Millionen Aktien |
| `10DayAverageTradingVolume` | Durchschnittliches Handelsvolumen (10 Tage) | Millionen Aktien |

### Enterprise Value (EV)

| Feld | Beschreibung | Formel |
|------|--------------|--------|
| `enterpriseValue` | Unternehmenswert | Marktkapitalisierung + Nettoverschuldung |
| `evRevenueTTM` | EV/Revenue (TTM) | Enterprise Value ÷ Umsatz |
| `evEbitdaTTM` | EV/EBITDA (TTM) | Enterprise Value ÷ EBITDA |
| `currentEv/freeCashFlowAnnual` | EV/FCF (jährlich) | Enterprise Value ÷ freier Cashflow |

### EBITDA-Kennzahlen

| Feld | Beschreibung | Details |
|------|--------------|---------|
| `ebitdPerShareAnnual` | EBITDA pro Aktie (jährlich) | EBITDA ÷ Aktienanzahl |
| `ebitdPerShareTTM` | EBITDA pro Aktie (TTM) | Wie oben für TTM |
| `ebitdaCagr5Y` | EBITDA CAGR (5 Jahre) | Compound Annual Growth Rate |
| `ebitdaInterimCagr5Y` | EBITDA Interim CAGR (5 Jahre) | Zwischenwachstumsrate |

### Wachstums-CAGRs (Compound Annual Growth Rates)

| Feld | Beschreibung | Zeitraum |
|------|--------------|----------|
| `tbvCagr5Y` | Tangible Book Value CAGR | 5 Jahre |
| `capexCagr5Y` | Capital Expenditure CAGR | 5 Jahre |
| `focfCagr5Y` | Free Operating Cash Flow CAGR | 5 Jahre |

### 5-Jahres-Durchschnitte

| Feld | Beschreibung | Details |
|------|--------------|---------|
| `netProfitMargin5Y` | Durchschnittliche Nettogewinnmarge | 5 Jahre |
| `grossMargin5Y` | Durchschnittliche Bruttomarge | 5 Jahre |
| `operatingMargin5Y` | Durchschnittliche operative Marge | 5 Jahre |
| `pretaxMargin5Y` | Durchschnittliche Vorsteuergewinnmarge | 5 Jahre |
| `netMarginGrowth5Y` | Nettomargenwachstum | 5 Jahre |
| `roe5Y` | Durchschnittliche ROE | 5 Jahre |
| `roa5Y` | Durchschnittliche ROA | 5 Jahre |
| `roi5Y` | Durchschnittliche ROI | 5 Jahre |

---

## Beispiel: ptbvAnnual erklärt

**ptbvAnnual = Price-to-Tangible-Book-Value (Annual)**

**Berechnung:**
```
ptbvAnnual = Aktienkurs ÷ Materieller Buchwert pro Aktie
```

**Materieller Buchwert = Eigenkapital - Immaterielle Vermögenswerte**

**Was sind immaterielle Vermögenswerte?**
- Goodwill (Firmenwert bei Übernahmen)
- Patente und Lizenzen
- Markenrechte
- Software und Technologie

**Interpretation für CG (Carlyle Group):**
- `ptbvAnnual: 3.5575`
- `tangibleBookValuePerShareAnnual: 14.2123`
- `bookValuePerShareAnnual: 15.6975`

→ Der Aktienkurs liegt bei etwa 3.56× des materiellen Buchwerts
→ Investoren zahlen 3.56 Dollar für jeden Dollar an "echten" Vermögenswerten

**Vergleich:**
- `pbAnnual: 3.2209` (mit immateriellen Vermögenswerten)
- `ptbvAnnual: 3.5575` (nur materielle Werte)

→ Unterschied zeigt, dass ~9% des Buchwerts aus immateriellen Vermögenswerten besteht

**Verwendung:**
- **Konservative Bewertung**: ptbv zeigt "echten" Wert besser als pb
- **Vergleich**: Besonders wichtig bei Tech-Firmen (viele immaterielle Assets)
- **Risikobewertung**: Niedriges ptbv = Aktie nahe am materiellen Wert = Sicherheitsmarge

---

## Abkürzungen

- **TTM**: Trailing Twelve Months (letzte 12 Monate)
- **YoY**: Year-over-Year (Vorjahresvergleich)
- **YTD**: Year-to-Date (seit Jahresbeginn)
- **RFY**: Restated Fiscal Year (bereinigte Geschäftsjahre)
- **CAGR**: Compound Annual Growth Rate (durchschnittliche jährliche Wachstumsrate)
- **EBITDA**: Earnings Before Interest, Taxes, Depreciation, and Amortization
- **EPS**: Earnings Per Share (Gewinn pro Aktie)
- **ROE**: Return on Equity (Eigenkapitalrendite)
- **ROA**: Return on Assets (Gesamtkapitalrendite)
- **ROI**: Return on Investment (Kapitalrendite)
- **P/E**: Price-to-Earnings (Kurs-Gewinn-Verhältnis)
- **P/B**: Price-to-Book (Kurs-Buchwert-Verhältnis)
- **P/S**: Price-to-Sales (Kurs-Umsatz-Verhältnis)
- **FCF**: Free Cash Flow (freier Cashflow)
- **EV**: Enterprise Value (Unternehmenswert)

---

## Datei-Zeitstempel

Die Datei enthält zusätzlich:
- `fetchDate`: Datum des API-Aufrufs (ISO 8601 Format)
- `isin`: International Securities Identification Number
- `timestamp`: Unix-Zeitstempel des letzten Quote-Updates

---

## Quelle

Alle Daten stammen von der **FinnHub API**:
- Profile: `/stock/profile2`
- Metrics: `/stock/metric?metric=all`
- Quote: `/quote`

**API-Dokumentation**: https://finnhub.io/docs/api
