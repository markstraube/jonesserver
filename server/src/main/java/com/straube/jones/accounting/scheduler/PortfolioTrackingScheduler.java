package com.straube.jones.accounting.scheduler;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.straube.jones.accounting.dto.BudgetDto;
import com.straube.jones.accounting.repository.AccountingRepository;
import com.straube.jones.accounting.service.PortfolioService;
import com.straube.jones.db.DayCounter;
import com.straube.jones.model.User;
import com.straube.jones.repository.UserRepository;

@Component
public class PortfolioTrackingScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioTrackingScheduler.class);

    private final UserRepository userRepository;
    private final PortfolioService portfolioService;
    private final AccountingRepository accountingRepository;

    @Autowired
    public PortfolioTrackingScheduler(UserRepository userRepository, 
                                      PortfolioService portfolioService,
                                      AccountingRepository accountingRepository) {
        this.userRepository = userRepository;
        this.portfolioService = portfolioService;
        this.accountingRepository = accountingRepository;
    }

    @Scheduled(fixedRate = 30000)
    public void trackPortfolios() {
        List<User> users = userRepository.findAll();
        LocalTime now = LocalTime.now();
        boolean keepMe = isKeepTime(now);
        int dayCounter = (int) DayCounter.now();

        for (User user : users) {
            try {
                processUser(user.getUsername(), dayCounter, keepMe);
            } catch (Exception e) {
                logger.error("Error tracking portfolio for user {}", user.getUsername(), e);
            }
        }
    }

    private void processUser(String user, int dayCounter, boolean keepMe) {
        // "Hole aktuelle Portfolio-Positionen" & "Portfolio-Wert berechnen"
        // PortfolioService already implements fetching active positions and summing value
        double currentPortfolioValue = portfolioService.fetchCurrentPortfolioValue(user);

        // "Budget aktualisieren: double delta = portfolioValue - previousPortfolioValue; newBudget = currentBudget + delta;"
        // We need previous portfolio value.
        // We can get it from latest tPerformance.
        
        double previousPortfolioValue = 0.0;
        double currentBudget = 0.0;
        
        var latestOpt = accountingRepository.getLatestBudget(user);
        if (latestOpt.isPresent()) {
            previousPortfolioValue = latestOpt.get().getPortfolio();
            currentBudget = latestOpt.get().getBudget();
        }

        double delta = currentPortfolioValue - previousPortfolioValue;
        double newBudget = currentBudget + delta;
        double newCash = newBudget - currentPortfolioValue; // Should match previous cash unless budget logic drift?
        
        // Actually: newBudget = (Cash + OldPortfolio) + (NewPortfolio - OldPortfolio) = Cash + NewPortfolio.
        // So Cash remains constant (unrealized gains don't change cash).
        // But we calculated NewBudget from OldBudget + Delta.
        // Let's verify Cash:
        // Cash = NewBudget - NewPortfolio = (OldBudget + Delta) - NewPortfolio
        // = (OldCash + OldPortfolio + Delta) - NewPortfolio
        // = OldCash + OldPortfolio + (NewPrice - OldPrice) - NewPrice
        // = OldCash + (OldPrice + NewPrice - OldPrice) - NewPrice
        // = OldCash.
        // Correct. Cash is stable.

        if (latestOpt.isPresent()) {
             newCash = latestOpt.get().getCash(); // explicit stability
        } else {
             newCash = 0.0; // or initial logic
        }

        accountingRepository.savePerformance(user, newBudget, currentPortfolioValue, newCash, dayCounter, keepMe, java.sql.Timestamp.valueOf(LocalDateTime.now()));
    }
    
    private boolean isKeepTime(LocalTime now) {
        // 22:00 Uhr (lokale Zeite zone implied by LocalTime.now(), ±5 Minuten Toleranz)
        return now.getHour() == 22 && now.getMinute() <= 5;
    }

    // Weekly cleanup: Monday 6:00
    @Scheduled(cron = "0 0 6 * * MON") 
    public void cleanupData() {
        List<User> users = userRepository.findAll();
        LocalDateTime threshold = LocalDateTime.now().with(LocalTime.MIDNIGHT); // "Aktueller Montag 00:00 Uhr"
        
        for (User user : users) {
             accountingRepository.cleanupPerformance(user.getUsername(), threshold);
        }
    }
}
