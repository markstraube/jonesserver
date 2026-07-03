package com.trading.marketdata.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per snapshot fetch per ticker. Hybrid layout: everything that intraday time-series
 * SQL will filter/aggregate on lives in scalar columns; the full nested structures (OI ladder,
 * unusual activity, news) are preserved verbatim in JSON columns so no information is lost
 * and later analyses can reach into them with MySQL 8 JSON functions.
 *
 * Schema is created via docs/persistence-schema.sql (ddl-auto stays 'none' so the app never
 * blocks or mutates the DB at startup — deliberate, HOME-40 also serves other projects).
 */
@Entity
@Table(name = "market_snapshot", indexes = {
        @Index(name = "idx_ticker_ts", columnList = "ticker, snapshotTs")
})
public class SnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 12)
    private String ticker;

    @Column(nullable = false)
    private Instant snapshotTs;

    @Column(length = 10)
    private String marketState;

    // --- Quote scalars ---
    private Double price;
    private Double changePct;
    private Double prevClose;
    @Column(name = "open_price")
    private Double open;
    private Double high;
    private Double low;
    private Long volume;

    // --- Options scalars ---
    private Double putCallRatioFlow;   // OptionsData.putCallRatio: today's chain-wide volume ratio
    private Double iv;                 // 30d implied volatility of the underlying (IBKR tick 24)
    private Double hv;                 // 30d historical volatility (IBKR tick 23)
    private Double ivRank;
    private Double maxPain;
    private Long oiCallTotal;          // window aggregates, precomputed for SQL
    private Long oiPutTotal;
    private Double oiPutCallRatio;
    private Long uaCallVolume;
    private Long uaPutVolume;
    private Double uaCallNotionalUsd;
    private Double uaPutNotionalUsd;

    // --- Short data scalars ---
    private Double shortFloat;
    private Double daysToCover;
    private Double instOwn;

    // --- Full-fidelity JSON payloads ---
    @Column(columnDefinition = "json")
    private String oiProfileJson;

    @Column(columnDefinition = "json")
    private String unusualActivityJson;

    @Column(columnDefinition = "json")
    private String newsJson;

    // --- Getters/Setters (JPA) ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public Instant getSnapshotTs() { return snapshotTs; }
    public void setSnapshotTs(Instant snapshotTs) { this.snapshotTs = snapshotTs; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Double getChangePct() { return changePct; }
    public void setChangePct(Double changePct) { this.changePct = changePct; }
    public Double getPrevClose() { return prevClose; }
    public void setPrevClose(Double prevClose) { this.prevClose = prevClose; }
    public Double getOpen() { return open; }
    public void setOpen(Double open) { this.open = open; }
    public Double getHigh() { return high; }
    public void setHigh(Double high) { this.high = high; }
    public Double getLow() { return low; }
    public void setLow(Double low) { this.low = low; }
    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }
    public String getMarketState() { return marketState; }
    public void setMarketState(String marketState) { this.marketState = marketState; }
    public Double getIv() { return iv; }
    public void setIv(Double iv) { this.iv = iv; }
    public Double getHv() { return hv; }
    public void setHv(Double hv) { this.hv = hv; }
    public Double getPutCallRatioFlow() { return putCallRatioFlow; }
    public void setPutCallRatioFlow(Double putCallRatioFlow) { this.putCallRatioFlow = putCallRatioFlow; }
    public Double getIvRank() { return ivRank; }
    public void setIvRank(Double ivRank) { this.ivRank = ivRank; }
    public Double getMaxPain() { return maxPain; }
    public void setMaxPain(Double maxPain) { this.maxPain = maxPain; }
    public Long getOiCallTotal() { return oiCallTotal; }
    public void setOiCallTotal(Long oiCallTotal) { this.oiCallTotal = oiCallTotal; }
    public Long getOiPutTotal() { return oiPutTotal; }
    public void setOiPutTotal(Long oiPutTotal) { this.oiPutTotal = oiPutTotal; }
    public Double getOiPutCallRatio() { return oiPutCallRatio; }
    public void setOiPutCallRatio(Double oiPutCallRatio) { this.oiPutCallRatio = oiPutCallRatio; }
    public Long getUaCallVolume() { return uaCallVolume; }
    public void setUaCallVolume(Long uaCallVolume) { this.uaCallVolume = uaCallVolume; }
    public Long getUaPutVolume() { return uaPutVolume; }
    public void setUaPutVolume(Long uaPutVolume) { this.uaPutVolume = uaPutVolume; }
    public Double getUaCallNotionalUsd() { return uaCallNotionalUsd; }
    public void setUaCallNotionalUsd(Double uaCallNotionalUsd) { this.uaCallNotionalUsd = uaCallNotionalUsd; }
    public Double getUaPutNotionalUsd() { return uaPutNotionalUsd; }
    public void setUaPutNotionalUsd(Double uaPutNotionalUsd) { this.uaPutNotionalUsd = uaPutNotionalUsd; }
    public Double getShortFloat() { return shortFloat; }
    public void setShortFloat(Double shortFloat) { this.shortFloat = shortFloat; }
    public Double getDaysToCover() { return daysToCover; }
    public void setDaysToCover(Double daysToCover) { this.daysToCover = daysToCover; }
    public Double getInstOwn() { return instOwn; }
    public void setInstOwn(Double instOwn) { this.instOwn = instOwn; }
    public String getOiProfileJson() { return oiProfileJson; }
    public void setOiProfileJson(String oiProfileJson) { this.oiProfileJson = oiProfileJson; }
    public String getUnusualActivityJson() { return unusualActivityJson; }
    public void setUnusualActivityJson(String unusualActivityJson) { this.unusualActivityJson = unusualActivityJson; }
    public String getNewsJson() { return newsJson; }
    public void setNewsJson(String newsJson) { this.newsJson = newsJson; }
}
