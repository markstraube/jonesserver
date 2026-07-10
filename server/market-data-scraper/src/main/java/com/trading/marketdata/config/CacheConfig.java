package com.trading.marketdata.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${cache.quote.ttl-seconds:60}")
    private long quoteTtl;

    @Value("${cache.short.ttl-seconds:3600}")
    private long shortTtl;

    @Value("${cache.news.ttl-seconds:180}")
    private long newsTtl;

    // conId is static metadata for a symbol — it never changes. Long TTL just to allow
    // eventual self-healing if IBKR ever re-lists a symbol under a new conId.
    @Value("${cache.ibkr-conid.ttl-seconds:604800}")
    private long conIdTtl;

    // Strikes/expirations for a chain almost never change intraday (new expiries get listed
    // occasionally, not urgent to catch same-day).
    @Value("${cache.option-chain.ttl-seconds:43200}")
    private long optionChainTtl;

    // Open interest is published once per day before market open and does not update
    // intraday — unlike volume, which does and is therefore never cached (see
    // OptionActivityService). A multi-hour TTL avoids re-subscribing to the OI generic tick
    // on every poll while still picking up the next day's published figure well within a day.
    @Value("${cache.option-oi.ttl-seconds:14400}")
    private long optionOpenInterestTtl;

    // How long to remember "IBKR confirmed this strike/expiry/right doesn't exist" (error 200)
    // before asking again. Moderate TTL: long enough to stop repeatedly wasting a round trip on
    // an invalid combo within a polling session, short enough to notice if the exchange lists a
    // new strike later in the day (common for near-term expiries after a large price move).
    @Value("${cache.invalid-contract.ttl-seconds:3600}")
    private long invalidContractTtl;

    // Day memory of strikes already scanned per ticker+expiry ("Tagesgedächtnis"). Keeps
    // OI-heavy levels observable after the nearest-strikes window has drifted away with the
    // price (2026-07-06: MU gapped +3% and the 950 put wall — defended to 28 cents two
    // sessions earlier — silently left the scan window). Keys embed the NY trading date, so
    // the TTL is not the day boundary — it just needs to comfortably outlive one session
    // including pre/after hours. 18h default.
    @Value("${cache.oi-day-memory.ttl-seconds:64800}")
    private long oiDayMemoryTtl;

    // Per-contract OI day memory for the UA stage-2 OI-delta join: today's published OI per
    // ticker:expiry:strike:right:date, compared against the most recent previous session's
    // entry (lookback 5 calendar days in OptionActivityService). TTL 6 days keeps the
    // invariant TTL > lookback + the partial write/read days: an entry written intraday on
    // day D must still be alive when day D+5 looks it up at a later wall-clock time — a
    // 4-day TTL made lookback day 5 unreachable (evicted before it could ever be found).
    // 6 days also survives a long weekend (Fri→Tue after a Monday holiday) plus one missed
    // scan day. Unlike oiDayMemory above, whose value only prioritizes sticky strikes
    // within one day.
    @Value("${cache.oi-contract-day-memory.ttl-seconds:518400}")
    private long oiContractDayMemoryTtl;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("quotes",
                Caffeine.newBuilder().expireAfterWrite(quoteTtl, TimeUnit.SECONDS).maximumSize(500).build());
        manager.registerCustomCache("short",
                Caffeine.newBuilder().expireAfterWrite(shortTtl, TimeUnit.SECONDS).maximumSize(500).build());
        manager.registerCustomCache("news",
                Caffeine.newBuilder().expireAfterWrite(newsTtl, TimeUnit.SECONDS).maximumSize(500).build());
        manager.registerCustomCache("ibkrConId",
                Caffeine.newBuilder().expireAfterWrite(conIdTtl, TimeUnit.SECONDS).maximumSize(1000).build());
        manager.registerCustomCache("optionChain",
                Caffeine.newBuilder().expireAfterWrite(optionChainTtl, TimeUnit.SECONDS).maximumSize(500).build());
        manager.registerCustomCache("optionOpenInterest",
                Caffeine.newBuilder().expireAfterWrite(optionOpenInterestTtl, TimeUnit.SECONDS).maximumSize(5000).build());
        manager.registerCustomCache("invalidOptionContract",
                Caffeine.newBuilder().expireAfterWrite(invalidContractTtl, TimeUnit.SECONDS).maximumSize(5000).build());
        manager.registerCustomCache("oiDayMemory",
                Caffeine.newBuilder().expireAfterWrite(oiDayMemoryTtl, TimeUnit.SECONDS).maximumSize(2000).build());
        manager.registerCustomCache("oiContractDayMemory",
                Caffeine.newBuilder().expireAfterWrite(oiContractDayMemoryTtl, TimeUnit.SECONDS).maximumSize(20000).build());
        return manager;
    }
}
