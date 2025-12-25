package com.straube.jones.trader.filter;

import java.time.LocalDate;

/**
 * Ereignisse, die zu starken Kurssprüngen führen können.
 */
public class EventFilter {

    /**
     * Nächstes Quartalszahlen-Datum.
     * Quelle: Unternehmenskalender
     */
    private LocalDate nextEarningsDate;

    /**
     * Nächstes Dividenden-Datum.
     * Quelle: Börse / Unternehmen
     */
    private LocalDate nextDividendDate;

    /**
     * Anzahl Handelstage bis zu den nächsten Earnings.
     *
     * Berechnung:
     * Börsentage zwischen heute und Earnings-Datum
     */
    private int tradingDaysUntilEarnings;

    public LocalDate getNextEarningsDate() { return nextEarningsDate; }
    public void setNextEarningsDate(LocalDate nextEarningsDate) { this.nextEarningsDate = nextEarningsDate; }
    public LocalDate getNextDividendDate() { return nextDividendDate; }
    public void setNextDividendDate(LocalDate nextDividendDate) { this.nextDividendDate = nextDividendDate; }
    public int getTradingDaysUntilEarnings() { return tradingDaysUntilEarnings; }
    public void setTradingDaysUntilEarnings(int tradingDaysUntilEarnings) { this.tradingDaysUntilEarnings = tradingDaysUntilEarnings; }
}
