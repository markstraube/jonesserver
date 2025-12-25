package com.straube.jones.trader.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.HashMap;

@Service
public class MockCompanyService {
    
    private static final Map<String, String> COMPANIES = new HashMap<>();
    
    static {
        COMPANIES.put("AAPL", "Apple Inc.");
        COMPANIES.put("MSFT", "Microsoft Corp.");
        COMPANIES.put("SAP", "SAP SE");
        COMPANIES.put("TSLA", "Tesla Inc.");
        COMPANIES.put("NVDA", "NVIDIA Corp.");
    }

    public String getCompanyName(String symbol) {
        return COMPANIES.getOrDefault(symbol, "Unknown Company");
    }
}
