package com.sorarebot;

import com.sorarebot.api.SorareApiClient;
import com.sorarebot.blockchain.BlockchainService;
import com.sorarebot.model.Player;
import com.sorarebot.persistence.TransactionRepository;
import com.sorarebot.persistence.WatchlistRepository;
import com.sorarebot.trading.TradingEngine;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    
    public SorareTradingBot(Properties config) throws Exception {
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
        
        this.tradingEngine = new TradingEngine(apiClient, watchlistRepo, transactionRepo);
    }
    
    /**
     * Start the trading bot.
     */
    public void start() {
        try {
            // Check available balance before starting
            BigDecimal balance = blockchainService.getEthBalance();
            LOGGER.info("Current ETH balance: " + balance);
            
            if (balance.compareTo(BigDecimal.valueOf(0.1)) < 0) {
                LOGGER.warning("Low ETH balance: " + balance + " ETH. Consider adding funds.");
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
        System.out.println("Commands: add <player_id> <rarity>, remove <player_id>, list, balance, quit");
        
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
                        
                    case "quit":
                    case "exit":
                        running = false;
                        break;
                        
                    default:
                        System.out.println("Unknown command: " + parts[0]);
                        System.out.println("Commands: add <player_id> <rarity>, remove <player_id>, list, balance, quit");
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
     * Shutdown all services and clean up resources.
     */
    public void shutdown() {
        try {
            tradingEngine.stop();
            blockchainService.shutdown();
            
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
