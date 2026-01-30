package com.straube.jones.accounting.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.straube.jones.accounting.dto.BudgetDto;
import com.straube.jones.accounting.repository.AccountingRepository;
import com.straube.jones.db.DayCounter;

@Service
public class BudgetService {

    private final AccountingRepository repository;
    private final PortfolioService portfolioService;

    @Autowired
    public BudgetService(AccountingRepository repository, PortfolioService portfolioService) {
        this.repository = repository;
        this.portfolioService = portfolioService;
    }

    public BudgetDto getBudget(String user) {
        Optional<BudgetDto> budgetOpt = repository.getLatestBudget(user);
        if (budgetOpt.isPresent()) {
            BudgetDto b = budgetOpt.get();
            // recalculate portfolio? Requirements says "Budget Endpoints ... GET /budget ... Response: { budget: ... }"
            // But "Budget" is usually sum(Portfolio + Cash).
            // DTO has all three.
            // If I return stored budget, it might be stale (scheduler runs every 30s).
            // Maybe force fresh portfolio calc?
            // "Aktuelles Gesamtvermögen abrufen" -> implied fresh?
            // "GET /api/accounting/portfolio ... (Berechnung: Hole alle Positionen...)" suggests endpoint does recalc.
            // If /budget endpoint returns just budget, maybe it uses stored value?
            // Let's use stored value for speed, scheduler updates it frequently.
            return b;
        } else {
            // Default for new user?
            return new BudgetDto(0.0, 0.0, 0.0, ""); // budget, portfolio, cash, date
        }
    }

    public BudgetDto setBudget(String user, Double newBudget) {
        // "Cash wird angepasst: Cash_neu = Budget_neu - Portfolio_aktuell"
        double currentPortfolio = portfolioService.fetchCurrentPortfolioValue(user);
        double newCash = newBudget - currentPortfolio;
        
        if (newBudget < 0) {
            throw new IllegalArgumentException("Budget cannot be negative");
        }
        
        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.now());
        repository.savePerformance(user, newBudget, currentPortfolio, newCash, DayCounter.now(), false, timestamp); // keepMe=false? Updated manually.
        
        return new BudgetDto(newBudget, currentPortfolio, newCash, timestamp.toInstant().toString());
    }
    
    public BudgetDto updateFromTransaction(String user, double cashDelta, double portfolioDelta) {
        // Calculate based on previous
        BudgetDto current = getBudget(user);
        double newCash = current.getCash() + cashDelta;
        double newPortfolio = current.getPortfolio() + portfolioDelta;
        double newBudget = newCash + newPortfolio; // Or current.getBudget() if transaction doesn't change net worth?
        // Buy: Cash -X, Portfolio +X -> Budget same.
        // Sell: Cash +Y, Portfolio -X -> Budget changes by (Y-X) profit/loss.
        
        // This method saves the new state to tPerformance
        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.now());
        repository.savePerformance(user, newBudget, newPortfolio, newCash, DayCounter.now(), false, timestamp);
        return new BudgetDto(newBudget, newPortfolio, newCash, timestamp.toInstant().toString());
    }
}
