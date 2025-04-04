package com.sorarebot;

import com.sorarebot.api.SorareApiClient;
import com.sorarebot.blockchain.BlockchainService;
import com.sorarebot.model.Player;
import com.sorarebot.notification.NotificationService;
import com.sorarebot.persistence.CardPreferenceRepository;
import com.sorarebot.persistence.HighPriorityPlayerRepository;
import com.sorarebot.persistence.TransactionRepository;
import com.sorarebot.persistence.WatchlistRepository;
import com.sorarebot.security.EmergencyStop;
import com.sorarebot.trading.TradingEngine;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application class for the Sorare Trading Bot.
 * Handles initialization, configuration, and user interaction.
 */
public class SorareTradingBot {
    private static final Logger LOGGER = Logger.getLogger(SorareTradingBot.class.getName());
    
    private final SorareApiClient apiClient;
    private final BlockchainService blockchainService;
    private final TradingEngine tradingEngine;
    private final WatchlistRepository watchlistRepo;
    private final TransactionRepository transactionRepo;
    private final CardPreferenceRepository cardPreferenceRepo;
    private final HighPriorityPlayerRepository highPriorityRepo;
    private final NotificationService notificationService;
    
    // Security components
    private final EmergencyStop emergencyStop;
    
    public SorareTradingBot(Properties config) throws Exception {
        // Initialize emergency stop system
        String emergencyStopDir = config.getProperty("security.emergency.dir", "./security/emergency");
        long fileCheckIntervalMs = Long.parseLong(
            config.getProperty("security.emergency.check.interval.ms", "5000"));
        
        this.emergencyStop = new EmergencyStop(emergencyStopDir, fileCheckIntervalMs);
        this.emergencyStop.startFileWatcher();
        
        // Load Ethereum credentials
        String walletPassword = config.getProperty("wallet.password");
        String walletPath = config.getProperty("wallet.path");
        Credentials credentials = WalletUtils.loadCredentials(walletPassword, walletPath);
        LOGGER.info("Loaded wallet for address: " + credentials.getAddress());
        
        // Initialize services and repositories
        String ethereumNodeUrl = config.getProperty("ethereum.node.url");
        this.blockchainService = new BlockchainService(ethereumNodeUrl, credentials);
        
        String sorareApiToken = config.getProperty("sorare.api.token");
        this.apiClient = new SorareApiClient(sorareApiToken, credentials);
        
        String dbPath = config.getProperty("db.path", "./sorarebot.db");
        this.watchlistRepo = new WatchlistRepository(dbPath);
        this.transactionRepo = new TransactionRepository(dbPath);
        this.cardPreferenceRepo = new CardPreferenceRepository(dbPath);
        
        // Initialize high-priority player repository
        String highPriorityPlayersFile = config.getProperty(
            "high_priority.players.file", "./data/high_priority_players.txt");
        this.highPriorityRepo = new HighPriorityPlayerRepository(dbPath, highPriorityPlayersFile);
        
        // Initialize notification service
        String emailAddress = config.getProperty("notification.email");
        String smtpHost = config.getProperty("notification.smtp.host");
        String smtpPort = config.getProperty("notification.smtp.port");
        String smtpUsername = config.getProperty("notification.smtp.username");
        String smtpPassword = config.getProperty("notification.smtp.password");
        boolean enabledEmail = Boolean.parseBoolean(config.getProperty("notification.email.enabled", "false"));
        
        this.notificationService = new NotificationService(
                emailAddress, smtpHost, smtpPort, smtpUsername, smtpPassword, enabledEmail);
        
        // Get transaction rate limit from config
        int maxTransactionsPerHour = Integer.parseInt(
            config.getProperty("trading.max.transactions.per.hour", "5"));
        LOGGER.info("Transaction rate limit: " + maxTransactionsPerHour + " per hour");
        
        // Initialize trading engine with security components
        this.tradingEngine = new TradingEngine(
                apiClient, 
                watchlistRepo, 
                transactionRepo, 
                cardPreferenceRepo,
                highPriorityRepo,
                notificationService,
                maxTransactionsPerHour,
                emergencyStop);
    }
    
    /**
     * Start the trading bot.
     */
    public void start() {
        try {
            // Check if emergency stop is active
            if (emergencyStop.isEmergencyStopActive()) {
                String reason = emergencyStop.getEmergencyStopReason();
                LOGGER.severe("Cannot start trading bot - Emergency stop is active: " + reason);
                System.out.println("EMERGENCY STOP ACTIVE: " + reason);
                System.out.println("Please resolve the issue and clear the emergency stop before starting.");
                return;
            }
            
            // Check available balance before starting
            BigDecimal balance = blockchainService.getEthBalance();
            LOGGER.info("Current ETH balance: " + balance);
            
            if (balance.compareTo(BigDecimal.valueOf(0.1)) < 0) {
                LOGGER.warning("Low ETH balance: " + balance + " ETH. Consider adding funds.");
                System.out.println("WARNING: Low ETH balance: " + balance + " ETH");
            }
            
            // Start the trading engine
            tradingEngine.start();
            
            // Start the command line interface
            startCommandLineInterface();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error starting trading bot", e);
        }
    }
    
    /**
     * Start a simple command line interface for interaction.
     */
    private void startCommandLineInterface() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        
        System.out.println("Sorare Trading Bot");
        System.out.println("Commands:");
        System.out.println("  add <player_id> <rarity>        - Add player to watchlist");
        System.out.println("  remove <player_id>              - Remove player from watchlist");
        System.out.println("  list                           - List watched players");
        System.out.println("  balance                        - Show ETH balance");
        System.out.println("  serial add <number> [rarity]    - Add favorite serial number");
        System.out.println("  serial remove <number> [rarity] - Remove favorite serial number");
        System.out.println("  serial list                    - List favorite serial numbers");
        System.out.println("  jersey on|off                  - Enable/disable jersey mint notifications");
        System.out.println("  jersey price <eth_amount>      - Set max price for jersey mints (0 for no limit)");
        System.out.println("  priority add <player_id> <rarity> - Add player to high-priority list");
        System.out.println("  priority remove <player_id>    - Remove player from high-priority list");
        System.out.println("  priority list                  - List high-priority players");
        System.out.println("  priority history <player_id> <rarity> - Show sales history for player");
        System.out.println("  emergency status               - Check emergency stop status");
        System.out.println("  emergency stop <reason>        - Trigger emergency stop");
        System.out.println("  emergency clear                - Clear emergency stop");
        System.out.println("  emergency clear-force          - Force clear emergency stop");
        System.out.println("  transactions                   - Show recent transactions");
        System.out.println("  quit                           - Exit the program");
        
        while (running) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            String[] parts = input.split("\\s+");
            
            if (parts.length == 0) {
                continue;
            }
            
            try {
                switch (parts[0].toLowerCase()) {
                    case "add":
                        if (parts.length >= 3) {
                            String playerId = parts[1];
                            String rarity = parts[2];
                            Player player = new Player(playerId, "", rarity);
                            tradingEngine.addToWatchlist(player);
                            System.out.println("Added player to watchlist: " + playerId);
                        } else {
                            System.out.println("Usage: add <player_id> <rarity>");
                        }
                        break;
                        
                    case "remove":
                        if (parts.length >= 2) {
                            String playerId = parts[1];
                            tradingEngine.removeFromWatchlist(playerId);
                            System.out.println("Removed player from watchlist: " + playerId);
                        } else {
                            System.out.println("Usage: remove <player_id>");
                        }
                        break;
                        
                    case "list":
                        System.out.println("Watchlist:");
                        watchlistRepo.getWatchlist().forEach(p -> 
                            System.out.println(p.getId() + " - " + p.getName() + " (" + p.getRarity() + ")"));
                        break;
                        
                    case "balance":
                        BigDecimal balance = blockchainService.getEthBalance();
                        System.out.println("Current ETH balance: " + balance);
                        break;
                        
                    case "transactions":
                        System.out.println("Recent transactions:");
                        transactionRepo.getRecentTransactions(10).forEach(System.out::println);
                        break;
                    
                    case "serial":
                        handleSerialCommands(parts);
                        break;
                        
                    case "jersey":
                        handleJerseyCommands(parts);
                        break;
                        
                    case "priority":
                        handlePriorityCommands(parts);
                        break;
                        
                    case "emergency":
                        handleEmergencyCommands(parts);
                        break;
                        
                    case "quit":
                    case "exit":
                        running = false;
                        break;
                        
                    default:
                        System.out.println("Unknown command: " + parts[0]);
                        System.out.println("Type 'help' for a list of commands");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                LOGGER.log(Level.SEVERE, "Error processing command: " + input, e);
            }
        }
        
        System.out.println("Shutting down...");
        shutdown();
    }
    
    /**
     * Handle serial number commands.
     */
    private void handleSerialCommands(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: serial add|remove|list ...");
            return;
        }
        
        switch (parts[1].toLowerCase()) {
            case "add":
                if (parts.length >= 3) {
                    try {
                        int serialNumber = Integer.parseInt(parts[2]);
                        String rarity = parts.length >= 4 ? parts[3] : null;
                        cardPreferenceRepo.addFavoriteSerial(serialNumber, rarity);
                        System.out.println("Added favorite serial number: " + serialNumber + 
                                        (rarity != null ? " (Rarity: " + rarity + ")" : ""));
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid serial number: " + parts[2]);
                    }
                } else {
                    System.out.println("Usage: serial add <number> [rarity]");
                }
                break;
                
            case "remove":
                if (parts.length >= 3) {
                    try {
                        int serialNumber = Integer.parseInt(parts[2]);
                        String rarity = parts.length >= 4 ? parts[3] : null;
                        cardPreferenceRepo.removeFavoriteSerial(serialNumber, rarity);
                        System.out.println("Removed favorite serial number: " + serialNumber);
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid serial number: " + parts[2]);
                    }
                } else {
                    System.out.println("Usage: serial remove <number> [rarity]");
                }
                break;
                
            case "list":
                System.out.println("Favorite serial numbers:");
                cardPreferenceRepo.getFavoriteSerials().forEach(serial -> 
                    System.out.println("  " + serial));
                break;
                
            default:
                System.out.println("Unknown serial command: " + parts[1]);
                System.out.println("Valid commands: serial add, serial remove, serial list");
        }
    }
    
    /**
     * Handle jersey mint commands.
     */
    private void handleJerseyCommands(String[] parts) {
        if (parts.length < 2) {
            boolean enabled = cardPreferenceRepo.isJerseyMintEnabled();
            Double maxPrice = cardPreferenceRepo.getJerseyMintMaxPrice();
            System.out.println("Jersey mint notifications: " + (enabled ? "enabled" : "disabled"));
            System.out.println("Jersey mint max price: " + 
                             (maxPrice != null ? maxPrice + " ETH" : "no limit"));
            return;
        }
        
        switch (parts[1].toLowerCase()) {
            case "on":
                cardPreferenceRepo.setJerseyMintEnabled(true);
                System.out.println("Jersey mint notifications enabled");
                break;
                
            case "off":
                cardPreferenceRepo.setJerseyMintEnabled(false);
                System.out.println("Jersey mint notifications disabled");
                break;
                
            case "price":
                if (parts.length >= 3) {
                    try {
                        double price = Double.parseDouble(parts[2]);
                        if (price <= 0) {
                            cardPreferenceRepo.setJerseyMintMaxPrice(null);
                            System.out.println("Jersey mint max price: no limit");
                        } else {
                            cardPreferenceRepo.setJerseyMintMaxPrice(price);
                            System.out.println("Jersey mint max price set to: " + price + " ETH");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid price: " + parts[2]);
                    }
                } else {
                    Double currentPrice = cardPreferenceRepo.getJerseyMintMaxPrice();
                    System.out.println("Current jersey mint max price: " + 
                                     (currentPrice != null ? currentPrice + " ETH" : "no limit"));
                }
                break;
                
            default:
                System.out.println("Unknown jersey command: " + parts[1]);
                System.out.println("Valid commands: jersey on, jersey off, jersey price");
        }
    }
    
    /**
     * Handle high-priority player commands.
     */
    private void handlePriorityCommands(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: priority add|remove|list|history ...");
            return;
        }
        
        switch (parts[1].toLowerCase()) {
            case "add":
                if (parts.length >= 4) {
                    String playerId = parts[2];
                    String rarity = parts[3];
                    
                    boolean added = tradingEngine.addHighPriorityPlayer(playerId, rarity);
                    if (added) {
                        System.out.println("Added high-priority player: " + playerId + " (" + rarity + ")");
                    } else {
                        System.out.println("Player already in high-priority list: " + playerId);
                    }
                } else {
                    System.out.println("Usage: priority add <player_id> <rarity>");
                }
                break;
                
            case "remove":
                if (parts.length >= 3) {
                    String playerId = parts[2];
                    
                    boolean removed = tradingEngine.removeHighPriorityPlayer(playerId);
                    if (removed) {
                        System.out.println("Removed high-priority player: " + playerId);
                    } else {
                        System.out.println("Player not found in high-priority list: " + playerId);
                    }
                } else {
                    System.out.println("Usage: priority remove <player_id>");
                }
                break;
                
            case "list":
                System.out.println("High-priority players:");
                List<Player> highPriorityPlayers = tradingEngine.getHighPriorityPlayers();
                if (highPriorityPlayers.isEmpty()) {
                    System.out.println("  No high-priority players");
                } else {
                    highPriorityPlayers.forEach(p -> 
                        System.out.println("  " + p.getId() + " (" + p.getRarity() + ")"));
                }
                break;
                
            case "history":
                if (parts.length >= 4) {
                    String playerId = parts[2];
                    String rarity = parts[3];
                    
                    List<BigDecimal> salesHistory = tradingEngine.getPlayerSalesHistory(playerId, rarity);
                    System.out.println("Sales history for " + playerId + " (" + rarity + "):");
                    
                    if (salesHistory.isEmpty()) {
                        System.out.println("  No sales history found");
                    } else {
                        BigDecimal sum = BigDecimal.ZERO;
                        for (int i = 0; i < salesHistory.size(); i++) {
                            BigDecimal price = salesHistory.get(i);
                            sum = sum.add(price);
                            System.out.println("  " + (i + 1) + ". " + price + " ETH");
                        }
                        
                        // Calculate average
                        BigDecimal average = sum.divide(BigDecimal.valueOf(salesHistory.size()), 6, BigDecimal.ROUND_HALF_UP);
                        System.out.println("Average: " + average + " ETH");
                    }
                } else {
                    System.out.println("Usage: priority history <player_id> <rarity>");
                }
                break;
                
            default:
                System.out.println("Unknown priority command: " + parts[1]);
                System.out.println("Valid commands: priority add, priority remove, priority list, priority history");
        }
    }
    
    /**
     * Handle emergency stop commands.
     */
    private void handleEmergencyCommands(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: emergency status|stop|clear|clear-force");
            return;
        }
        
        switch (parts[1].toLowerCase()) {
            case "status":
                boolean isActive = emergencyStop.isEmergencyStopActive();
                System.out.println("Emergency stop: " + (isActive ? "ACTIVE" : "Inactive"));
                if (isActive) {
                    String reason = emergencyStop.getEmergencyStopReason();
                    System.out.println("Reason: " + reason);
                }
                break;
                
            case "stop":
                if (parts.length >= 3) {
                    // Combine all remaining parts as the reason
                    StringBuilder reasonBuilder = new StringBuilder();
                    for (int i = 2; i < parts.length; i++) {
                        if (i > 2) reasonBuilder.append(" ");
                        reasonBuilder.append(parts[i]);
                    }
                    
                    String reason = reasonBuilder.toString();
                    emergencyStop.triggerEmergencyStop(reason);
                    System.out.println("Emergency stop triggered: " + reason);
                } else {
                    System.out.println("Usage: emergency stop <reason>");
                }
                break;
                
            case "clear":
                boolean cleared = emergencyStop.clearEmergencyStop(false);
                if (cleared) {
                    System.out.println("Emergency stop cleared successfully");
                } else {
                    System.out.println("Failed to clear emergency stop");
                    System.out.println("If you're sure it's safe to proceed, use: emergency clear-force");
                }
                break;
                
            case "clear-force":
                boolean forcedClear = emergencyStop.clearEmergencyStop(true);
                if (forcedClear) {
                    System.out.println("Emergency stop cleared with force");
                } else {
                    System.out.println("Failed to clear emergency stop");
                }
                break;
                
            default:
                System.out.println("Unknown emergency command: " + parts[1]);
                System.out.println("Valid commands: status, stop, clear, clear-force");
        }
    }
    
    /**
     * Shutdown all services and clean up resources.
     */
    public void shutdown() {
        try {
            tradingEngine.stop();
            blockchainService.shutdown();
            emergencyStop.stopFileWatcher();
            
            LOGGER.info("Trading bot shut down successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error shutting down trading bot", e);
        }
    }
    
    /**
     * Main method to start the application.
     */
    public static void main(String[] args) {
        try {
            // Load configuration
            String configPath = args.length > 0 ? args[0] : "config.properties";
            Properties config = loadConfig(configPath);
            
            // Create and start the bot
            SorareTradingBot bot = new SorareTradingBot(config);
            bot.start();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start trading bot", e);
            System.exit(1);
        }
    }
    
    /**
     * Load configuration from a properties file.
     */
    private static Properties loadConfig(String configPath) throws Exception {
        Properties config = new Properties();
        
        Path path = Paths.get(configPath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Configuration file not found: " + configPath);
        }
        
        try (FileInputStream fis = new FileInputStream(new File(configPath))) {
            config.load(fis);
        }
        
        validateConfig(config);
        
        return config;
    }
    
    /**
     * Validate that all required configuration properties are present.
     */
    private static void validateConfig(Properties config) {
        String[] requiredProps = {
            "wallet.path", "wallet.password", "ethereum.node.url", "sorare.api.token"
        };
        
        for (String prop : requiredProps) {
            if (!config.containsKey(prop)) {
                throw new IllegalArgumentException("Missing required configuration property: " + prop);
            }
        }
    }
}