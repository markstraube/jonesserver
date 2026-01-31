package com.straube.jones.controller;

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
import com.straube.jones.model.User;
import com.straube.jones.repository.UserRepository;

@RestController
@RequestMapping("/api/accounting")
public class AccountingController {

    private final BudgetService budgetService;
    private final PortfolioService portfolioService;
    private final CashService cashService;
    private final PerformanceService performanceService;
    private final UserRepository userRepository;

    @Autowired
    public AccountingController(BudgetService budgetService, 
                                PortfolioService portfolioService,
                                CashService cashService,
                                PerformanceService performanceService,
                                UserRepository userRepository) {
        this.budgetService = budgetService;
        this.portfolioService = portfolioService;
        this.cashService = cashService;
        this.performanceService = performanceService;
        this.userRepository = userRepository;
    }

    private User getCurrentUser()
    {
        UserDetails userDetails = (UserDetails)SecurityContextHolder.getContext()
                                                                    .getAuthentication()
                                                                    .getPrincipal();
        return userRepository.findByUsername(userDetails.getUsername())
                             .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // 1. Budget Endpoints

    @GetMapping("/budget")
    public ResponseEntity<BudgetDto> getBudget() {
        User user = getCurrentUser();
        return ResponseEntity.ok(budgetService.getBudget(user));
    }

    @PostMapping("/budget")
    public ResponseEntity<BudgetDto> setBudget(@RequestBody BudgetDto request) {
        User user = getCurrentUser();
        if (request.getBudget() == null || request.getBudget() < 0) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(budgetService.setBudget(user, request.getBudget()));
    }

    // 2. Portfolio Endpoints

    @GetMapping("/portfolio")
    public ResponseEntity<PortfolioValueDto> getPortfolio() {
        User user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.getPortfolioValue(user));
    }

    @PostMapping("/portfolio")
    public ResponseEntity<BudgetDto> setPortfolioValue(@RequestBody PortfolioValueDto request) {
        User user = getCurrentUser();
       
        BudgetDto current = budgetService.getBudget(user);
        double budget = current.getBudget();
        double newPortfolio = request.getPortfolioValue();
        
        double newCash = budget - newPortfolio;
        if (newCash < 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        BudgetDto result = budgetService.updateFromTransaction(user, newCash - current.getCash(), newPortfolio - current.getPortfolio());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/portfolio/buy")
    public ResponseEntity<TransactionDto> buyStock(@RequestBody TransactionDto transaction) {
        User user = getCurrentUser();
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
        User user = getCurrentUser();
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
        User user = getCurrentUser();
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
        User user = getCurrentUser();
        return ResponseEntity.ok(performanceService.getPerformance(user, from, to));
    }

    @GetMapping("/performance/week")
    public ResponseEntity<List<PerformanceDto>> getWeekPerformance() {
        User user = getCurrentUser();
        return ResponseEntity.ok(performanceService.getWeekPerformance(user));
    }
}
