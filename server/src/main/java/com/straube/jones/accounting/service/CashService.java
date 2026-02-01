package com.straube.jones.accounting.service;


import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.straube.jones.accounting.dto.BudgetDto;
import com.straube.jones.accounting.repository.AccountingRepository;
import com.straube.jones.model.User;

@Service
public class CashService
{

    private final AccountingRepository repository;

    @Autowired
    public CashService(AccountingRepository repository)
    {
        this.repository = repository;
    }


    public Double getCash(User user)
    {
        Optional<BudgetDto> budget = repository.getLatestBudget(user);
        // "Berechnung: Cash = Budget - Portfolio"
        // If I trust the columns in tPerformance
        if (budget.isPresent())
        { return budget.get().getCash(); }
        return 0.0;
    }
}
