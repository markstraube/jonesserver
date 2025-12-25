package com.straube.jones.trader.service;

import com.straube.jones.trader.dto.DailyPrice;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class MockMarketDataService {

    public List<DailyPrice> getMarketData(String symbol) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate date = LocalDate.now();
        double price = 150.0;
        Random random = new Random(symbol.hashCode());

        for (int i = 0; i < 250; i++) {
            DailyPrice dp = new DailyPrice();
            dp.setDate(date.minusDays(i));
            
            double change = (random.nextDouble() - 0.45) * 2; 
            price += change;
            if (price < 10) price = 10;

            dp.setClose(price);
            dp.setOpen(price - (random.nextDouble() - 0.5));
            dp.setHigh(Math.max(dp.getOpen(), dp.getClose()) + random.nextDouble());
            dp.setLow(Math.min(dp.getOpen(), dp.getClose()) - random.nextDouble());
            dp.setVolume(1_000_000 + random.nextInt(500_000));
            
            prices.add(dp);
        }
        return prices;
    }
}
