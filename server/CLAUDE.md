# CLAUDE.md — Project Context & Snapshot Instructions

## Owner Profile
- Active trader, Java developer, 60+, ~6h/week training
- Expects radical honesty: no softening, no moralizing, no reassuring conclusions
- German is primary language — respond in German unless code/technical output
- Evidence-critical rigor across all domains (trading, tech, supplements, science)

---

## Startup Task (run once per session)

Beim Start dieser Session führe automatisch folgende Schritte aus, **bevor** du auf den ersten User-Prompt antwortest:

```
1. Scanne alle pom.xml im Projektroot und Submodulen
2. Lese src/main/java Struktur (2 Ebenen tief) pro Modul
3. Lese src/main/resources/application*.properties / application*.yml
4. Prüfe ob ein /docs oder /notes Ordner existiert — lese README.md falls vorhanden
5. Lies git log --oneline -20 (letzte 20 Commits)
6. Suche nach offenen TODOs: grep -r "TODO\|FIXME\|HACK" src/ --include="*.java" -l
7. Fasse alles als "SESSION START SNAPSHOT" aus — kompakt, strukturiert
```

Gib den Snapshot als erstes aus — dann warte auf den User-Prompt.

---

## Bekannte Projekte (Stand: Juni 2026)

### A004 — Multi-Agent Incident Analysis
- **Zweck:** AI-assisted distributed middleware diagnostics, 40+ Pod-Instanzen
- **Stack:** Spring Boot 3.x, Java 21, MySQL
- **Architektur:** Hypothesis-driven 4-stage pipeline
  ```
  CaseClassifier → HypothesisGenerator → DynamicInvestigatorDispatcher → EvidenceEvaluator
  ```
- **Status:** In Entwicklung — beim Start aktuellen Stand aus Code verifizieren

### commandline — Trading Backtester
- **Zweck:** Strategy-Backtesting mit Yahoo Finance Daten
- **Stack:** Java 21, MySQL (InnoDB, tuned buffer pool)
- **Kernkomponente:** `Strategy001` — 3 Strategien:
  - Daily Open/Close
  - Buy-and-Hold
  - Optimized Midpoint
- **Validierung:** 282 Trading Days pro ISIN
- **Gelöste Probleme:** Yahoo Finance Crumb/Cookie-Layer bereits implementiert
- **Status:** Beim Start aktuellen Stand aus Code verifizieren

### market-data-scraper (neu, noch nicht gebaut)
- **Zweck:** Intraday Marktdaten für aktives Trading — Quote, Options, Short Interest, News
- **Stack:** Spring Boot 3.3.x, Java 21, WebFlux, Jsoup, Springdoc OpenAPI
- **Datenquellen:** Yahoo Finance, Finviz, Barchart, MarketChameleon
- **API:** REST + Swagger UI auf Port 8080
- **Spec:** Liegt in `/docs/market-data-scraper-prompt.md` (falls vorhanden)

---

## Coding Conventions (nicht verhandelbar)

```
- Java 21: Records, Pattern Matching, Virtual Threads wo sinnvoll
- Spring Boot 3.3.x
- Keine Stubs, keine TODOs im generierten Code — immer vollständig implementiert
- Jsoup für HTML-Scraping
- WebFlux / WebClient für async HTTP (kein RestTemplate)
- Caffeine für Caching
- Optional<T> intern, null im JSON bei fehlenden Daten
- Graceful degradation statt Exceptions nach außen
- Alle Scraper stateless
- User-Agent rotation + Rate Limiting bei externen Calls
```

---

## Aktuelle Trading-Thesen (Kontext für marktbezogene Fragen)

### SNDK (SanDisk)
- ATH ~$2.191 (18. Juni), Schlusskurs 21. Juni: $2.209
- WDC Share Swap Closing: **heute, 22. Juni 2026** — 1.038.681 Aktien, >$2 Mrd.
- Bull-Case: Pure-Play NAND, Nasdaq-100 Index-Flows, AI-Storage-Supercycle
- Bear-Case: RSI ~99 (Polymarket: "most overbought stock in history"), Konsens-Ziel $1.751 (-21%), Put/Call >1.5, WDC-Exit als institutionelles Verkaufssignal
- Beta: 3.47, Float: 146 Mio. Aktien, Short Float: 6.5%
- Nächste Earnings: 13. August 2026

### MU (Micron Technology)
- Earnings: **24. Juni 2026**
- Bearish Thesis: "Sold out through 2027"-Narrativ vollständig eingepreist, neues ATH als FOMO-Maximierung engineered
- Bernstein heute: Kursziel $1.300 (von $510), DRAM-Shortage tiefer als erwartet

### IFX (Infineon)
- GAN Patent-Sieg vor deutschem Gericht gegen Innoscience
- Bull-Case: AI Datacenter Revenue-Trajectory
- Bear-Case: China Automotive Headwind
- Hexensabbat-Session: 19. Juni 2026

---

## Snapshot-Output Format

Claude Code soll beim Session-Start folgendes ausgeben:

```
═══════════════════════════════════════
SESSION START SNAPSHOT — [Datum/Zeit]
═══════════════════════════════════════

PROJEKTE GEFUNDEN:
  ✓ [Projektname] — [artifact-id aus pom.xml] v[version]
    Packages: [Top-Level-Packages]
    Klassen: [Anzahl .java Dateien]
    Letzte Änderung: [aus git log]

OFFENE TODOs/FIXMEs:
  [Datei]: [TODO-Text]

LETZTE COMMITS:
  [hash] [message]
  ...

DELTA ZU BEKANNTEM STAND:
  [Was wurde seit letzter Beschreibung geändert — soweit erkennbar]

BEREIT.
═══════════════════════════════════════
```

---

## Kontext-Export für Claude.ai Chat

Wenn der User `!export-context` eingibt, generiere eine kompakte Markdown-Zusammenfassung des aktuellen Projektstands, die direkt in einen Claude.ai Chat eingefügt werden kann:

```markdown
## Code-Snapshot [Datum]

### [Projektname]
- Tatsächliche Package-Struktur: ...
- Schlüsselklassen: ...
- Offene Punkte: ...
- Letzte Commits: ...
```

Dieser Export überbrückt die Lücke zwischen Claude Code (Filesystem-Zugriff) 
und Claude.ai Chat (Trading/Analyse-Kontext).

---

## Kontext-Import aus Claude.ai Chat

Wenn der User einen `## Code-Snapshot` Block einfügt, verarbeite ihn als 
aktualisierte Wahrheit über den Projektstand — höhere Priorität als 
die statischen Beschreibungen oben in dieser Datei.

---

## Was Claude Code NICHT tun soll

- Keine unsolicited Refactoring-Vorschläge
- Kein Hinweis auf "best practices" die nicht explizit angefragt wurden
- Keine Rückfragen bei eindeutigen Aufgaben — direkt implementieren
- Keine Zusammenfassungen am Ende ("I've created the file...") — Ergebnis spricht für sich
