package com.straube.jones.trader.dasboard;

import java.time.LocalDate;

public class EventSection {

    /** Datum der nächsten Quartalszahlen */
    private LocalDate earningsDate;

    /** Tage bis zu den Earnings */
    private Integer daysUntilEarnings;

    /** Warnhinweis für das Trading */
    private String warning;

    public LocalDate getEarningsDate() { return earningsDate; }
    public void setEarningsDate(LocalDate earningsDate) { this.earningsDate = earningsDate; }
    public Integer getDaysUntilEarnings() { return daysUntilEarnings; }
    public void setDaysUntilEarnings(Integer daysUntilEarnings) { this.daysUntilEarnings = daysUntilEarnings; }
    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
}
