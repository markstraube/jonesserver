package com.straube.jones.accounting.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.straube.jones.accounting.dto.BudgetDto;
import com.straube.jones.accounting.dto.PerformanceDto;
import com.straube.jones.accounting.dto.PortfolioValueDto;
import com.straube.jones.accounting.dto.TransactionDto;
import com.straube.jones.accounting.service.BudgetService;
import com.straube.jones.accounting.service.CashService;
import com.straube.jones.accounting.service.PerformanceService;
import com.straube.jones.accounting.service.PortfolioService;
import com.straube.jones.db.DayCounter;

@RestController
@RequestMapping("/api/accounting")
public class AccountingController {

    private final BudgetService budgetService;
    private final PortfolioService portfolioService;
    private final CashService cashService;
    private final PerformanceService performanceService;

    @Autowired
    public AccountingController(BudgetService budgetService, 
                                PortfolioService portfolioService,
                                CashService cashService,
                                PerformanceService performanceService) {
        this.budgetService = budgetService;
        this.portfolioService = portfolioService;
        this.cashService = cashService;
        this.performanceService = performanceService;
    }

    private String getCurrentUsername() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        } else {
            return principal.toString();
        }
    }

    // 1. Budget Endpoints

    @GetMapping("/budget")
    public ResponseEntity<BudgetDto> getBudget() {
        String user = getCurrentUsername();
        return ResponseEntity.ok(budgetService.getBudget(user));
    }

    @PostMapping("/budget")
    public ResponseEntity<BudgetDto> setBudget(@RequestBody BudgetDto request) {
        String user = getCurrentUsername();
        if (request.getBudget() == null || request.getBudget() < 0) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(budgetService.setBudget(user, request.getBudget()));
    }

    // 2. Portfolio Endpoints

    @GetMapping("/portfolio")
    public ResponseEntity<PortfolioValueDto> getPortfolio() {
        String user = getCurrentUsername();
        return ResponseEntity.ok(portfolioService.getPortfolioValue(user));
    }

    @PostMapping("/portfolio")
    public ResponseEntity<BudgetDto> setPortfolioValue(@RequestBody PortfolioValueDto request) {
        String user = getCurrentUsername();
        // Manually set portfolio value?
        // Prompt: "Portfolio-Wert manuell setzen ... Validation: Portfolio + Cash <= Budget"
        // Also "Response: { portfolioValue: ..., cash: ..., budget: ... }"
        // This implies recalculating Cash? Or strictly setting Portfolio Value and keeping Budget fixed?
        // "Validation: Portfolio + Cash <= Budget" implies we check if new Portfolio fits in Budget assuming Cash is NOT adjusted?
        // OR Cash IS adjusted?
        // Prompt says "Response: ... cash: 90000, budget: 150000".
        // If I set Portfolio to 60k, and Budget is 150k.
        // Option A: Budget Fixed. Cash = Budget - Portfolio.
        // Option B: Cash Fixed. Budget = Portfolio + Cash.
        // The endpoint is `setPortfolioValue`.
        // If I set portfolio value, usually I update it.
        // If I follow `setBudget` logic (Cash adjusted), `setPortfolio` should also adjust Cash or Budget.
        // Prompt validation: "Portfolio + Cash <= Budget". This suggests Budget is the limit.
        // So likely: Update Portfolio. Check if Portfolio + CurrentCash <= CurrentBudget.
        // Note: Logic allows manual override of Portfolio Value (maybe for external assets).
        // BUT `tPortfolio` dictates calculated value.
        // If I manually set it, do I write to `tPerformance` (snapshot) or `tPortfolio` (positions)?
        // Prompt doesn't say "Update positions". It says "Portfolio-Wert manuell setzen".
        // And `tPerformance` stores the aggregate.
        // So this endpoint seemingly updates the *Accounting View* (tPerformance), possibly decoupling it from `tPortfolio`?
        // OR it's a way to inject "External" value.
        // I'll implement: Update `tPerformance` with new Portfolio Value. Recalculate Cash = Budget - Portfolio.
        
        BudgetDto current = budgetService.getBudget(user);
        double budget = current.getBudget();
        double newPortfolio = request.getPortfolioValue();
        
        // Wait, "Validation: Portfolio + Cash <= Budget"
        // If Cash is recalculated as `Budget - Portfolio`, then `Portfolio + (Budget - Portfolio) = Budget`. 
        // Always True (ignoring rounding).
        // UNLESS Cash is fixed?
        // If Cash is fixed, and Portfolio changes, then Budget must change.
        // But Validation implies Budget is a constraint.
        // Let's assume:
        // We have `cBudget` (total allocated).
        // We have `cCash` (available).
        // We have `cPortfolio` (invested).
        // If I say "My Portfolio is now 60k" (instead of 50k).
        // Did I make profit? (Budget increases).
        // Or did I move Cash to Portfolio? (Budget same, Cash decreases).
        // If this is "Manually set", distinct from "Buy/Sell", it might mean "Update Valuation".
        // If valuation increases, Budget (Net Worth) increases.
        // So Validation `P + C <= B` makes no sense if B increases.
        // It only makes sense if B is fixed cap.
        // If B is fixed cap, then `P + C <= B` means `P <= B - C`.
        // So if I set P=60k, and C=100k, B=150k.
        // 60 + 100 = 160 > 150. Conflict.
        // This validation implies "You cannot set Portfolio value higher than what fits in the Budget with current Cash".
        // Which implies Cash is NOT automatically reduced to compensate (that would be a Buy).
        // It implies we just assert the value.
        // What happens to the delta?
        // If I pass validation, do I update Budget? Reference: "Response: ... budget: 150000". Budget constant.
        // So P changed, C constant, B constant?
        // P=60, C=90, B=150. (original P=50, C=100. adjusted C happens?)
        // Example response says: sent P=60k. Response C=90k.
        // This means Cash WAS reduced.
        // So `Cash_new = Budget - P_new`.
        // Then `P_new + Cash_new = Budget`. Always valid?
        // Why "Conflict" validation?
        // Maybe "Cash <= Budget - Portfolio" ?
        // If P > Budget, then Cash < 0. That is the check.
        // "Validation: Portfolio + Cash <= Budget" might be "Portfolio <= Budget" (since Cash >= 0).
        // I will implement: NewCash = Budget - NewPortfolio. If NewCash < 0 -> 409 Conflict.
        
        double newCash = budget - newPortfolio;
        if (newCash < 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        
        // Update via BudgetService (which saves to tPerformance)
        // We treat it as if cash was moved.
        // We don't change tPortfolio positions! Just the accounting record.
        // This might cause drift between positions and accounting.
        // But prompt asks for it.
        
        // We must update tPerformance
        BudgetDto result = budgetService.updateFromTransaction(user, newCash - current.getCash(), newPortfolio - current.getPortfolio());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/portfolio/buy")
    public ResponseEntity<TransactionDto> buyStock(@RequestBody TransactionDto transaction) {
        String user = getCurrentUsername();
        try {
            double currentCash = cashService.getCash(user);
            TransactionDto result = portfolioService.buy(user, transaction, currentCash);
            
            // Sync Budget/Cash
            // Buy reduces Cash, Increases Portfolio. Net Budget change 0.
            double amount = transaction.getQuantity() * transaction.getPrice();
            budgetService.updateFromTransaction(user, -amount, amount);
            
            BudgetDto b = budgetService.getBudget(user);
            result.setCash(b.getCash());
            result.setPortfolioValue(b.getPortfolio());
            
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            if ("Insufficient funds".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).build();
            }
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/portfolio/sell")
    public ResponseEntity<TransactionDto> sellStock(@RequestBody TransactionDto transaction) {
        String user = getCurrentUsername();
        try {
            TransactionDto result = portfolioService.sell(user, transaction.getPositionId(), transaction.getQuantity(), transaction.getPrice());
            
            double amount = transaction.getQuantity() * transaction.getPrice();
            budgetService.updateFromTransaction(user, amount, -amount);
            
            BudgetDto b = budgetService.getBudget(user);
            result.setCash(b.getCash());
            result.setPortfolioValue(b.getPortfolio());

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build(); // 400 or 404
        }
    }

    // 3. Cash Endpoint

    @GetMapping("/cash")
    public ResponseEntity<BudgetDto> getCash() {
        String user = getCurrentUsername();
        // Returns object { "cash": 100000.00 }
        // BudgetDto has it.
        Double cash = cashService.getCash(user);
        BudgetDto response = new BudgetDto();
        response.setCash(cash);
        return ResponseEntity.ok(response);
    }

    // 4. Performance Endpoints

    @GetMapping("/performance")
    public ResponseEntity<List<PerformanceDto>> getPerformance(
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {
        String user = getCurrentUsername();
        return ResponseEntity.ok(performanceService.getPerformance(user, from, to));
    }

    @GetMapping("/performance/week")
    public ResponseEntity<List<PerformanceDto>> getWeekPerformance() {
        String user = getCurrentUsername();
        return ResponseEntity.ok(performanceService.getWeekPerformance(user));
    }
}
