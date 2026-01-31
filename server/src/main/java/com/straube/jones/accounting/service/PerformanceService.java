package com.straube.jones.accounting.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.straube.jones.accounting.dto.PerformanceDto;
import com.straube.jones.accounting.repository.AccountingRepository;
import com.straube.jones.db.DayCounter;
import com.straube.jones.model.User;

@Service
public class PerformanceService {

    private final AccountingRepository repository;

    @Autowired
    public PerformanceService(AccountingRepository repository) {
        this.repository = repository;
    }

    public List<PerformanceDto> getPerformance(User user, Long from, Long to) {
        long t = (to != null) ? to : DayCounter.before(90); // Default to 90 days ago? 
        // Prompt: "to (optional): ... Default: DayCounter.before(90)"
        // "from (optional): ... Default: DayCounter.yesterday()"
        // Usually ranges are From < To. But "before(90)" is smaller int than "today".
        // If DayCounter counts days from 2000, "90 days ago" < "today".
        // Wait, endpoint param names are "from" and "to".
        // Range: from=yesterday(big), to=ago(small)? Or reverse?
        // Let's assume params are DayCounter values.
        // "Hole alle Einträge... im Zeitraum".
        // I will assume the caller might pass them in any order or standard range.
        // Defaults: from=Yesterday, to=90 days ago.
        
        long f = (from != null) ? from : DayCounter.yesterday();
        long end = (to != null) ? to : DayCounter.before(90);

        long min = Math.min(f, end);
        long max = Math.max(f, end);    
        return repository.getPerformance(user, min, max);
    }

    public List<PerformanceDto> getWeekPerformance(User user) {
        return repository.getPerformanceLastDays(user, 5);
    }
}
