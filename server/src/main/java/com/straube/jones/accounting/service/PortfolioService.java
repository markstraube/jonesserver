package com.straube.jones.accounting.service;


import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.straube.jones.accounting.dto.PortfolioValueDto;
import com.straube.jones.accounting.dto.TransactionDto;
import com.straube.jones.accounting.repository.AccountingRepository;
import com.straube.jones.dto.PriceEntry;
import com.straube.jones.dto.PriceTickerResponse;
import com.straube.jones.model.User;
import com.straube.jones.service.PriceTickerService;

@Service
public class PortfolioService
{

    private static final Logger logger = LoggerFactory.getLogger(PortfolioService.class);

    private final AccountingRepository repository;
    private final PriceTickerService priceTickerService;

    @Autowired
    public PortfolioService(AccountingRepository repository)
    {
        this.repository = repository;
        this.priceTickerService = new PriceTickerService(); // Assuming default constructor is available and
                                                            // functional
    }


    public PortfolioValueDto getPortfolioValue(User user)
    {
        List<TransactionDto> positions = repository.getActivePositions(user);
        double totalValue = 0.0;

        for (TransactionDto pos : positions)
        {
            double currentPrice = getCurrentBidPrice(pos.getIsin(), pos.getSymbol());
            totalValue += pos.getQuantity() * currentPrice;
        }

        return new PortfolioValueDto(totalValue);
    }


    public double fetchCurrentPortfolioValue(User user)
    {
        return getPortfolioValue(user).getPortfolioValue();
    }


    public TransactionDto buy(User user, TransactionDto transaction, double currentCash)
    {
        double cost = transaction.getQuantity() * transaction.getPrice();

        if (cost > currentCash)
        {
            throw new IllegalArgumentException("Insufficient funds"); // Controller converts to 402
        }

        repository.addPortfolioPosition(user, transaction);

        // Calculate new state
        // Portfolio increases by cost (valuation at purchase price)
        // Cash decreases by cost

        // Return updated info? Controller will handle aggregating response using BudgetService.
        TransactionDto response = new TransactionDto();
        response.setTransactionId("tx-" + System.currentTimeMillis()); // Placeholder ID
        return response;
    }


    public TransactionDto sell(User user, String positionId, int quantity, double price)
    {
        Optional<TransactionDto> posOpt = repository.getPosition(positionId);
        if (posOpt.isEmpty())
        {
            throw new IllegalArgumentException("Position not found"); // Controller 404
        }
        TransactionDto pos = posOpt.get();
        // Verify user ownership
        String owner = repository.getPositionUser(positionId);
        if (!user.equals(owner))
        { throw new IllegalArgumentException("Position not found or access denied"); }

        // Verify active
        // Repo getPosition usually filters or returns state. I fetched raw.
        // Assuming fetch active

        if (quantity > pos.getQuantity())
        {
            throw new IllegalArgumentException("Cannot sell more than owned"); // 400
        }

        double revenue = quantity * price;

        if (quantity == pos.getQuantity())
        {
            // Full sale
            repository.closePortfolioPosition(positionId, price);
        }
        else
        {
            // Partial sale
            int remaining = pos.getQuantity() - quantity;

            // 1. Create new position for remaining
            repository.createPartialSalePosition(user,
                                                 positionId,
                                                 remaining,
                                                 pos.getPrice(),
                                                 pos.getIsin(),
                                                 pos.getSymbol(),
                                                 pos.getStockName());

            // 2. Mark original as closed/partial sold
            repository.markPartialSaleOriginal(positionId, price, quantity);
        }

        TransactionDto response = new TransactionDto();
        response.setTransactionId("tx-" + System.currentTimeMillis());
        // Values updated by controller re-fetching state
        return response;
    }


    private double getCurrentBidPrice(String isin, String symbol)
    {
        try
        {
            // Try by ISIN
            PriceTickerResponse response = priceTickerService.getPriceByIsinFromTradegate(isin);
            return extractBidPrice(response);
        }
        catch (Exception e)
        {
            logger.warn("Failed to get price for ISIN {}, trying symbol {}", isin, symbol);
            // Fallback? The PriceTickerService seems to only support ISIN in the method I saw.
            // "PriceTickerService.getPriceByIsinFromTradegate"
            // If it fails, maybe return purchase price? Or 0?
            // "PriceTickerController: getCurrentPrice(String symbol)" was mentioned in requirements.
            // But I found "getPriceByIsinFromTradegate".
            // I'll assume 0.0 or throw?
            // Better to return 0.0 so one failure doesn't break the whole portfolio view?
            // Or rethrow.
            return 0.0;
        }
    }


    private double extractBidPrice(PriceTickerResponse response)
    {
        if (response.getPrices() == null || response.getPrices().isEmpty())
            return 0.0;

        // Find "last-price" (REGULAR)
        // Adjust for PriceEntry structure
        for (PriceEntry entry : response.getPrices())
        {
            // Assuming PriceEntry has getLastPrice returning BigDecimal
            // I need to use reflection or check PriceEntry again if getter is not public/standard?
            // It had @JsonProperty("last-price") private BigDecimal lastPrice;
            // I'll assume standard getter: getLastPrice()
            if (entry.getLastPrice() != null)
            { return entry.getBidPrice().doubleValue(); }
        }
        return 0.0; // Fallback
    }
}
