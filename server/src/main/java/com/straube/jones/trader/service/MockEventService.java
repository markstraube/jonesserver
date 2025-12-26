package com.straube.jones.trader.service;


import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
public class MockEventService
{

    public LocalDate getNextEarningsDate(String symbol)
    {
        if ("AAPL".equalsIgnoreCase(symbol))
        { return LocalDate.now().plusDays(3); }
        if ("MSFT".equalsIgnoreCase(symbol))
        { return LocalDate.now().plusDays(20); }
        return LocalDate.now().plusDays(60);
    }


    public LocalDate getNextDividendDate(String symbol)
    {
        return LocalDate.now().plusDays(45);
    }
}
