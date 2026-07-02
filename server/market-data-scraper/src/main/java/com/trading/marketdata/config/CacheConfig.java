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

    @Value("${cache.options.ttl-seconds:120}")
    private long optionsTtl;

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

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("quotes",
                Caffeine.newBuilder().expireAfterWrite(quoteTtl, TimeUnit.SECONDS).maximumSize(500).build());
        manager.registerCustomCache("options",
                Caffeine.newBuilder().expireAfterWrite(optionsTtl, TimeUnit.SECONDS).maximumSize(500).build());
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
        return manager;
    }
}
