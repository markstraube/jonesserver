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
        return manager;
    }
}
