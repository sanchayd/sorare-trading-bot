package com.sorarebot.trading;

import com.sorarebot.api.SorareApiClient;
import com.sorarebot.model.Card;
import com.sorarebot.model.Offer;
import com.sorarebot.model.Player;
import com.sorarebot.notification.NotificationService;
import com.sorarebot.persistence.CardPreferenceRepository;
import com.sorarebot.persistence.HighPriorityPlayerRepository;
import com.sorarebot.persistence.TransactionRepository;
import com.sorarebot.persistence.WatchlistRepository;
import com.sorarebot.security.EmergencyStop;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core trading engine that implements the trading strategy.
 * Monitors market, makes buy/sell decisions, and executes trades.
 */
public class TradingEngine {
    private static final Logger LOGGER = Logger.getLogger(TradingEngine.class.getName());
    private static final BigDecimal MARKUP_PERCENTAGE = new BigDecimal("1.05"); // 5% markup
    private static final BigDecimal MAX_COUNTER_OFFER_DISCOUNT = new BigDecimal("0.95"); // 5% discount for counter offers
    
    private final SorareApiClient apiClient;
    private final WatchlistRepository watchlistRepo;
    private final TransactionRepository transactionRepo;
    private final CardPreferenceRepository cardPreferenceRepo;
    private final HighPriorityPlayerRepository highPriorityRepo;
    private final NotificationService notificationService;
    private final ScheduledExecutorService scheduler;
    private final PriceEvaluator priceEvaluator;
    
    // Transaction rate limiting
    private final Queue<Instant> recentTransactions = new ConcurrentLinkedQueue<>();
    private final int maxTransactionsPerHour;
    private static final Duration TRANSACTION_WINDOW = Duration.ofHours(1);
    
    // Emergency stop
    private final EmergencyStop emergencyStop;
    
    public TradingEngine(
            SorareApiClient apiClient, 
            WatchlistRepository watchlistRepo,
            TransactionRepository transactionRepo,
            CardPreferenceRepository cardPreferenceRepo,
            HighPriorityPlayerRepository highPriorityRepo,
            NotificationService notificationService,
            int maxTransactionsPerHour,
            EmergencyStop emergencyStop) {
        this.apiClient = apiClient;
        this.watchlistRepo = watchlistRepo;
        this.transactionRepo = transactionRepo;
        this.cardPreferenceRepo = cardPreferenceRepo;
        this.highPriorityRepo = highPriorityRepo;
        this.notificationService = notificationService;
        this.scheduler = Executors.newScheduledThreadPool(3); // Increased thread pool for high-priority monitoring
        this.priceEvaluator = new PriceEvaluator();
        this.maxTransactionsPerHour = maxTransactionsPerHour;
        this.emergencyStop = emergencyStop;
    }
    
    /**
     * Start the trading engine.
     * Sets up scheduled tasks for market monitoring and offer processing.
     */
    public void start() {
        // Check if emergency stop is active before starting
        if (emergencyStop != null && emergencyStop.isEmergencyStopActive()) {
            LOGGER.severe("Cannot start trading engine - Emergency stop is active: " + 
                       emergencyStop.getEmergencyStopReason());
            return;
        }
        
        // Schedule regular market monitoring every 5 minutes
        scheduler.scheduleAtFixedRate(
                this::monitorMarket,
                0,
                5,
                TimeUnit.MINUTES
        );
        
        // Schedule counter-offer processing every 30 minutes
        scheduler.scheduleAtFixedRate(
                this::processCounterOffers,
                2,
                30,
                TimeUnit.MINUTES
        );
        
        // Schedule high-priority player monitoring every 15 minutes
        scheduler.scheduleAtFixedRate(
                this::monitorHighPriorityPlayers,
                1, // Start after 1 minute to stagger with regular monitoring
                15,
                TimeUnit.MINUTES
        );
        
        LOGGER.info("Trading engine started at " + Instant.now());
    }
    
    /**
     * Stop the trading engine.
     */
    public void stop() {
        scheduler.shutdown();
        LOGGER.info("Trading engine stopped at " + Instant.now());
    }
    
    /**
     * Monitor the market for trading opportunities.
     * Checks each player on the watchlist for undervalued cards.
     */
    private void monitorMarket() {
        // Check if emergency stop is active
        if (emergencyStop != null && emergencyStop.isEmergencyStopActive()) {
            LOGGER.warning("Market monitoring skipped - Emergency stop is active");
            return;
        }
        
        LOGGER.info("Monitoring market at " + Instant.now());
        
        List<Player> watchlist = watchlistRepo.getWatchlist();
        
        for (Player player : watchlist) {
            try {
                // Skip high-priority players, they are handled separately
                if (highPriorityRepo.isHighPriorityPlayer(player.getId())) {
                    continue;
                }
                
                List<Card> availableCards = apiClient.getPlayerListings(player.getId());
                BigDecimal floorPrice = apiClient.getFloorPrice(player.getId(), player.getRarity());
                
                for (Card card : availableCards) {
                    evaluateAndTrade(card, floorPrice);
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error monitoring player: " + player.getName(), e);
            }
        }
    }
    
    /**
     * Monitor high-priority players using special buying rules.
     * Buys cards if they are below the average of the last 5 sales.
     */
    private void monitorHighPriorityPlayers() {
        // Check if emergency stop is active
        if (emergencyStop != null && emergencyStop.isEmergencyStopActive()) {
            LOGGER.warning("High-priority player monitoring skipped - Emergency stop is active");
            return;
        }
        
        LOGGER.info("Monitoring high-priority players at " + Instant.now() + " (15-minute cycle)");
        
        List<Player> highPriorityPlayers = highPriorityRepo.getHighPriorityPlayers();
        
        if (highPriorityPlayers.isEmpty()) {
            LOGGER.info("No high-priority players to monitor");
            return;
        }
        
        for (Player player : highPriorityPlayers) {
            try {
                List<Card> availableCards = apiClient.getPlayerListings(player.getId());
                
                if (availableCards.isEmpty()) {
                    LOGGER.info("No cards available for high-priority player: " + player.getId());
                    continue;
                }
                
                // Get the average of last 5 sales for this player
                BigDecimal averageSalePrice = highPriorityRepo.getAverageLastSalesPrice(
                    player.getId(), player.getRarity(), 5);
                
                // If we don't have enough sales history, use floor price as fallback
                if (averageSalePrice == null) {
                    BigDecimal floorPrice = apiClient.getFloorPrice(player.getId(), player.getRarity());
                    
                    // Record the floor price as a "sale" to start building history
                    highPriorityRepo.addSaleRecord(player.getId(), player.getRarity(), floorPrice);
                    
                    LOGGER.info("Not enough sales history for " + player.getId() + 
                               ", using floor price: " + floorPrice);
                    
                    // Use standard evaluation for now
                    for (Card card : availableCards) {
                        evaluateAndTrade(card, floorPrice);
                    }
                    continue;
                }
                
                LOGGER.info("Average sales price for " + player.getId() + ": " + averageSalePrice);
                
                // Find the lowest priced card
                Card lowestPricedCard = null;
                for (Card card : availableCards) {
                    if (lowestPricedCard == null || card.getPrice().compareTo(lowestPricedCard.getPrice()) < 0) {
                        lowestPricedCard = card;
                    }
                }
                
                // Special high-priority buying rule: Buy if price is below average of last 5 sales
                if (lowestPricedCard != null && 
                    lowestPricedCard.getPrice().compareTo(averageSalePrice) < 0) {
                    LOGGER.info("Found high-priority card below average sales price: " + 
                               lowestPricedCard.getId() + " - Price: " + lowestPricedCard.getPrice() + 
                               " ETH, Average: " + averageSalePrice + " ETH");
                    
                    // Use a special method for high-priority purchases
                    buyHighPriorityCard(lowestPricedCard, averageSalePrice);
                    
                    // Record the purchase price as a "sale" to update history
                    highPriorityRepo.addSaleRecord(
                        player.getId(), player.getRarity(), lowestPricedCard.getPrice());
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error monitoring high-priority player: " + player.getId(), e);
            }
        }
    }
    
    /**
     * Buy a high-priority card and list it for sale.
     * 
     * @param card The card to buy
     * @param averageSalePrice The average sale price, used for listing
     */
    private void buyHighPriorityCard(Card card, BigDecimal averageSalePrice) {
        // First check if we're within rate limits
        if (!canExecuteTransaction()) {
            LOGGER.info("Skipping high-priority card due to rate limit: " + card.getId());
            return;
        }
        
        // Check emergency stop
        if (emergencyStop != null && emergencyStop.isEmergencyStopActive()) {
            LOGGER.warning("Skipping high-priority card due to emergency stop: " + card.getId());
            return;
        }
        
        try {
            // Buy the card
            String txHash = apiClient.submitBuyOffer(card.getId(), card.getPrice());
            LOGGER.info("Bought high-priority card: " + card.getId() + " - Transaction: " + txHash);
            
            // Record the transaction execution for rate limiting
            recordTransactionExecution();
            
            // Record the transaction
            transactionRepo.recordPurchase(card.getId(), card.getPrice(), txHash);
            
            // Special case: List at average sale price or 5% markup, whichever is higher
            BigDecimal markupPrice = card.getPrice().multiply(MARKUP_PERCENTAGE)
                                    .setScale(6, RoundingMode.HALF_UP);
            BigDecimal listPrice = markupPrice.max(averageSalePrice);
            
            // List the card for sale
            String listingId = apiClient.listCardForSale(card.getId(), listPrice);
            LOGGER.info("Listed high-priority card for sale: " + card.getId() + 
                       " - Listing ID: " + listingId + " - Price: " + listPrice + " ETH");
            
            // Record the listing
            transactionRepo.recordListing(card.getId(), listPrice, listingId);
            
            // Notify about the purchase
            try {
                notificationService.notifySpecialCard(card, 
                    "High-priority card purchased below average sales price! Listed for " + listPrice + " ETH");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to send notification for high-priority card", e);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error buying high-priority card: " + card.getId(), e);
        }
    }
    
    /**
     * Check if a transaction can be executed based on rate limits.
     * 
     * @return true if we're within rate limits, false otherwise
     */
    private boolean canExecuteTransaction() {
        Instant oneHourAgo = Instant.now().minus(TRANSACTION_WINDOW);
        
        // Remove transactions older than one hour
        while (!recentTransactions.isEmpty() && recentTransactions.peek().isBefore(oneHourAgo)) {
            recentTransactions.poll();
        }
        
        // Check if we're under the limit
        if (recentTransactions.size() < maxTransactionsPerHour) {
            return true;
        }
        
        LOGGER.warning("Transaction rate limit reached: " + maxTransactionsPerHour + 
                   " transactions in the last hour. Skipping transaction.");
        return false;
    }
    
    /**
     * Record a transaction execution for rate limiting.
     */
    private void recordTransactionExecution() {
        recentTransactions.add(Instant.now());
    }
    
    /**
     * Evaluate a card and execute a trade if it meets criteria.
     * 
     * @param card The card to evaluate
     * @param floorPrice The current floor price for this player/rarity
     */
    private void evaluateAndTrade(Card card, BigDecimal floorPrice) {
        // Check if this is a special card
        checkForSpecialCard(card);
        
        // Get the card's price
        BigDecimal cardPrice = card.getPrice();
        
        // Check if the card is significantly undervalued
        if (priceEvaluator.isUndervalued(cardPrice, floorPrice)) {
            // First check if we're within rate limits
            if (!canExecuteTransaction()) {
                LOGGER.info("Skipping undervalued card due to rate limit: " + card.getId() + " - Price: " + 
                           cardPrice + " ETH, Floor: " + floorPrice + " ETH");
                return;
            }
            
            // Check if emergency stop is active
            if (emergencyStop != null && emergencyStop.isEmergencyStopActive()) {
                LOGGER.warning("Skipping undervalued card due to emergency stop: " + card.getId());
                return;
            }
            
            try {
                LOGGER.info("Found undervalued card: " + card.getId() + " - Price: " + cardPrice + 
                           " ETH, Floor: " + floorPrice + " ETH");
                
                // Buy the card
                String txHash = apiClient.submitBuyOffer(card.getId(), cardPrice);
                LOGGER.info("Bought card: " + card.getId() + " - Transaction: " + txHash);
                
                // Record the transaction execution for rate limiting
                recordTransactionExecution();
                
                // Record the transaction
                transactionRepo.recordPurchase(card.getId(), cardPrice, txHash);
                
                // If this is a high-priority player, record the purchase as a "sale" for history
                if (highPriorityRepo.isHighPriorityPlayer(card.getPlayerId())) {
                    highPriorityRepo.addSaleRecord(
                        card.getPlayerId(), card.getRarity(), cardPrice);
                }
                
                // Calculate selling price with 5% markup
                BigDecimal sellingPrice = cardPrice.multiply(MARKUP_PERCENTAGE).setScale(6, RoundingMode.HALF_UP);
                
                // List the card for sale
                String listingId = apiClient.listCardForSale(card.getId(), sellingPrice);
                LOGGER.info("Listed card for sale: " + card.getId() + " - Listing ID: " + listingId + 
                           " - Price: " + sellingPrice + " ETH");
                
                // Record the listing
                transactionRepo.recordListing(card.getId(), sellingPrice, listingId);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error trading card: " + card.getId(), e);
            }
        }
    }
    
    /**
     * Process counter-offers received for our listed cards.
     */
    private void processCounterOffers() {
        // Check if emergency stop is active
        if (emergencyStop != null && emergencyStop.isEmergencyStopActive()) {
            LOGGER.warning("Counter-offer processing skipped - Emergency stop is active");
            return;
        }
        
        LOGGER.info("Processing counter-offers at " + Instant.now());
        
        try {
            List<Offer> offers = apiClient.getReceivedOffers();
            
            for (Offer offer : offers) {
                evaluateCounterOffer(offer);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing counter-offers", e);
        }
    }
    
    /**
     * Evaluate a counter-offer and decide whether to accept it.
     * 
     * @param offer The counter-offer to evaluate
     */
    private void evaluateCounterOffer(Offer offer) {
        try {
            // Get our original purchase price for this card
            BigDecimal purchasePrice = transactionRepo.getPurchasePrice(offer.getCardId());
            
            if (purchasePrice == null) {
                LOGGER.warning("No purchase record found for card: " + offer.getCardId());
                return;
            }
            
            // Calculate minimum acceptable price (original price + some profit)
            BigDecimal minAcceptablePrice = purchasePrice.multiply(MAX_COUNTER_OFFER_DISCOUNT);
            
            // Check if emergency stop is active before accepting offer
            if (emergencyStop != null && emergencyStop.isEmergencyStopActive()) {
                LOGGER.warning("Skipping offer evaluation due to emergency stop: " + offer.getId());
                return;
            }
            
            // If the offer is acceptable, accept it
            if (offer.getPrice().compareTo(minAcceptablePrice) >= 0) {
                // Check transaction rate limiting for accepting offers too
                if (!canExecuteTransaction()) {
                    LOGGER.info("Skipping acceptable offer due to rate limit: " + offer.getId());
                    return;
                }
                
                String txHash = apiClient.acceptOffer(offer.getId());
                LOGGER.info("Accepted offer: " + offer.getId() + " - Transaction: " + txHash);
                
                // Record the transaction execution for rate limiting
                recordTransactionExecution();
                
                // Record the sale
                transactionRepo.recordSale(offer.getCardId(), offer.getPrice(), txHash);
                
                // If this is a card for a high-priority player, record the sale for history
                Card card = apiClient.getCardById(offer.getCardId());
                if (card != null && highPriorityRepo.isHighPriorityPlayer(card.getPlayerId())) {
                    highPriorityRepo.addSaleRecord(
                        card.getPlayerId(), card.getRarity(), offer.getPrice());
                }
            } else {
                LOGGER.info("Rejected offer: " + offer.getId() + " - Price too low: " + 
                           offer.getPrice() + " ETH (min: " + minAcceptablePrice + " ETH)");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error evaluating counter-offer: " + offer.getId(), e);
        }
    }
    
    /**
     * Check if a card matches any special criteria (jersey mint or favorite serial).
     * 
     * @param card The card to check
     */
    private void checkForSpecialCard(Card card) {
        // Check for jersey mint
        if (cardPreferenceRepo.isJerseyMintEnabled() && card.isJerseyMint()) {
            // Check if there's a price limit
            Double maxPrice = cardPreferenceRepo.getJerseyMintMaxPrice();
            if (maxPrice == null || card.getPrice().compareTo(BigDecimal.valueOf(maxPrice)) <= 0) {
                notificationService.notifySpecialCard(card, "Jersey mint - " + card.getPlayerJerseyNumber());
            }
        }
        
        // Check for favorite serial number
        Set<Integer> favoriteSerials = cardPreferenceRepo.getFavoriteSerials();
        if (favoriteSerials.contains(card.getSerial())) {
            notificationService.notifySpecialCard(card, "Favorite serial number - " + card.getSerialString());
        }
        
        // Check if this is a high-priority player with a good price
        if (highPriorityRepo.isHighPriorityPlayer(card.getPlayerId())) {
            // Get the average sales price
            BigDecimal averageSalePrice = highPriorityRepo.getAverageLastSalesPrice(
                card.getPlayerId(), card.getRarity(), 5);
                
            // If we have sales history and price is below average, notify
            if (averageSalePrice != null && card.getPrice().compareTo(averageSalePrice) < 0) {
                notificationService.notifySpecialCard(card, 
                    "High-priority player card below average sales price! Current: " + 
                    card.getPrice() + " ETH, Average: " + averageSalePrice + " ETH");
            }
        }
    }
    
    /**
     * Add a player to the watchlist.
     * 
     * @param player The player to add to the watchlist
     */
    public void addToWatchlist(Player player) {
        watchlistRepo.addToWatchlist(player);
        LOGGER.info("Added player to watchlist: " + player.getName());
    }
    
    /**
     * Remove a player from the watchlist.
     * 
     * @param playerId The ID of the player to remove
     */
    public void removeFromWatchlist(String playerId) {
        watchlistRepo.removeFromWatchlist(playerId);
        LOGGER.info("Removed player from watchlist: " + playerId);
    }
    
    /**
     * Add a player to the high-priority list.
     * 
     * @param playerId The player ID to add
     * @param rarity The rarity to monitor
     * @return true if added, false if already exists
     */
    public boolean addHighPriorityPlayer(String playerId, String rarity) {
        return highPriorityRepo.addHighPriorityPlayer(playerId, rarity);
    }
    
    /**
     * Remove a player from the high-priority list.
     * 
     * @param playerId The player ID to remove
     * @return true if removed, false if not found
     */
    public boolean removeHighPriorityPlayer(String playerId) {
        return highPriorityRepo.removeHighPriorityPlayer(playerId);
    }
    
    /**
     * Get the list of high-priority players.
     * 
     * @return List of high-priority players
     */
    public List<Player> getHighPriorityPlayers() {
        return highPriorityRepo.getHighPriorityPlayers();
    }
    
    /**
     * Get sales history for a high-priority player.
     * 
     * @param playerId The player ID to get history for
     * @param rarity The rarity to get history for
     * @return List of sales prices
     */
    public List<BigDecimal> getPlayerSalesHistory(String playerId, String rarity) {
        return highPriorityRepo.getPlayerSalesHistory(playerId, rarity);
    }
    
    /**
     * Inner class for evaluating card prices.
     */
    private static class PriceEvaluator {
        private static final BigDecimal SIGNIFICANT_DISCOUNT = new BigDecimal("0.85"); // 15% below floor
        
        /**
         * Determine if a card is significantly undervalued.
         * 
         * @param cardPrice The price of the card
         * @param floorPrice The current floor price for this player/rarity
         * @return true if the card is undervalued, false otherwise
         */
        public boolean isUndervalued(BigDecimal cardPrice, BigDecimal floorPrice) {
            // Calculate the threshold price (85% of floor price)
            BigDecimal thresholdPrice = floorPrice.multiply(SIGNIFICANT_DISCOUNT);
            
            // Card is undervalued if its price is below the threshold
            return cardPrice.compareTo(thresholdPrice) < 0;
        }
    }
}