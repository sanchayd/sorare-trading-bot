package com.sorarebot.persistence;

import com.sorarebot.model.Player;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for managing high-priority players that are monitored more frequently
 * and have special buying rules based on historical sales.
 */
public class HighPriorityPlayerRepository {
    private static final Logger LOGGER = Logger.getLogger(HighPriorityPlayerRepository.class.getName());
    
    private final String dbUrl;
    private final String playersFilePath;
    private final Map<String, String> playerRarityMap = new HashMap<>();
    
    public HighPriorityPlayerRepository(String dbPath, String playersFilePath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        this.playersFilePath = playersFilePath;
        
        // Initialize the database and create tables if they don't exist
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            initializeTables(conn);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error initializing high priority player database", e);
        }
        
        // Load players from file
        loadPlayersFromFile();
    }
    
    /**
     * Initialize the database tables.
     */
    private void initializeTables(Connection conn) throws SQLException {
        // Table for storing the last 5 sales for each player+rarity combination
        String createSalesHistoryTableSQL = 
            "CREATE TABLE IF NOT EXISTS player_sales_history (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "player_id TEXT NOT NULL," +
            "rarity TEXT NOT NULL," +
            "sale_price DECIMAL(18,6) NOT NULL," +
            "sale_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "UNIQUE (player_id, rarity, sale_timestamp)" +
            ")";
            
        // Create the tables
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSalesHistoryTableSQL);
            LOGGER.info("Player sales history table initialized");
        }
    }
    
    /**
     * Load high-priority players from the file.
     */
    private void loadPlayersFromFile() {
        File file = new File(playersFilePath);
        
        // Create file if it doesn't exist
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
                // Add example format
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    writer.write("# Format: player_id,rarity");
                    writer.newLine();
                    writer.write("# Example: 0x123456789abcdef,limited");
                    writer.newLine();
                }
                LOGGER.info("Created high-priority players file: " + playersFilePath);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error creating high-priority players file", e);
            }
            return;
        }
        
        // Read players from file
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNum = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                
                // Skip comments and empty lines
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Parse player_id,rarity format
                String[] parts = line.split(",");
                if (parts.length < 2) {
                    LOGGER.warning("Invalid format at line " + lineNum + ": " + line);
                    continue;
                }
                
                String playerId = parts[0].trim();
                String rarity = parts[1].trim();
                
                playerRarityMap.put(playerId, rarity);
                LOGGER.info("Loaded high-priority player: " + playerId + " (" + rarity + ")");
            }
            
            LOGGER.info("Loaded " + playerRarityMap.size() + " high-priority players");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading high-priority players file", e);
        }
    }
    
    /**
     * Get the list of high-priority players.
     * 
     * @return List of high-priority players
     */
    public List<Player> getHighPriorityPlayers() {
        List<Player> players = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : playerRarityMap.entrySet()) {
            String playerId = entry.getKey();
            String rarity = entry.getValue();
            
            // Name will be loaded later if needed
            players.add(new Player(playerId, "", rarity));
        }
        
        return players;
    }
    
    /**
     * Check if a player is in the high-priority list.
     * 
     * @param playerId The player ID to check
     * @return true if the player is high-priority, false otherwise
     */
    public boolean isHighPriorityPlayer(String playerId) {
        return playerRarityMap.containsKey(playerId);
    }
    
    /**
     * Get the rarity for a high-priority player.
     * 
     * @param playerId The player ID to get the rarity for
     * @return The rarity for the player, or null if not a high-priority player
     */
    public String getPlayerRarity(String playerId) {
        return playerRarityMap.get(playerId);
    }
    
    /**
     * Add a new sale record for a player.
     * 
     * @param playerId The player ID
     * @param rarity The rarity of the card
     * @param salePrice The sale price
     */
    public void addSaleRecord(String playerId, String rarity, BigDecimal salePrice) {
        LOGGER.debug("Adding sale record for " + playerId + " (" + rarity + "): " + salePrice + " ETH");
        
        String sql = "INSERT INTO player_sales_history (player_id, rarity, sale_price) VALUES (?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, playerId);
            pstmt.setString(2, rarity);
            pstmt.setBigDecimal(3, salePrice);
            
            int rows = pstmt.executeUpdate();
            LOGGER.debug("Inserted " + rows + " rows for sale record");
            
            // Delete older records if there are more than 5
            int prunedCount = pruneOldRecords(conn, playerId, rarity);
            if (prunedCount > 0) {
                LOGGER.debug("Pruned " + prunedCount + " old sale records for " + playerId);
            }
            
            LOGGER.info("Added sale record for " + playerId + " (" + rarity + "): " + salePrice);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error adding sale record", e);
        }
    }
    
    /**
     * Keep only the most recent 5 sale records for a player.
     * 
     * @return Number of records pruned
     */
    private int pruneOldRecords(Connection conn, String playerId, String rarity) throws SQLException {
        String sql = "DELETE FROM player_sales_history WHERE id IN (" +
                     "SELECT id FROM player_sales_history " +
                     "WHERE player_id = ? AND rarity = ? " +
                     "ORDER BY sale_timestamp DESC " +
                     "LIMIT -1 OFFSET 5)";
                     
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerId);
            pstmt.setString(2, rarity);
            
            int rowsDeleted = pstmt.executeUpdate();
            return rowsDeleted;
        }
    }
    
    /**
     * Get the average price of the last N sales for a player.
     * 
     * @param playerId The player ID
     * @param rarity The rarity of the card
     * @param count The number of sales to average (up to 5)
     * @return The average price, or null if not enough sales
     */
    public BigDecimal getAverageLastSalesPrice(String playerId, String rarity, int count) {
        if (count <= 0 || count > 5) {
            throw new IllegalArgumentException("Count must be between 1 and 5");
        }
        
        LOGGER.debug("Fetching average of last " + count + " sales for player: " + playerId + 
                   " (" + rarity + ")");
        
        String sql = "SELECT sale_price FROM player_sales_history " +
                     "WHERE player_id = ? AND rarity = ? " +
                     "ORDER BY sale_timestamp DESC LIMIT ?";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, playerId);
            pstmt.setString(2, rarity);
            pstmt.setInt(3, count);
            
            List<BigDecimal> prices = new ArrayList<>();
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    prices.add(rs.getBigDecimal("sale_price"));
                }
            }
            
            // Debug log the retrieved prices
            if (!prices.isEmpty()) {
                LOGGER.debug("Retrieved " + prices.size() + " sale prices for " + playerId + ": " + 
                           prices.toString());
            }
            
            // Check if we have enough sales
            if (prices.size() < count) {
                LOGGER.info("Not enough sales for " + playerId + " (" + rarity + "): " + 
                           prices.size() + "/" + count);
                return null;
            }
            
            // Calculate average
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal price : prices) {
                sum = sum.add(price);
            }
            
            BigDecimal average = sum.divide(BigDecimal.valueOf(prices.size()), 6, RoundingMode.HALF_UP);
            LOGGER.debug("Calculated average price for " + playerId + ": " + average + " ETH");
            
            return average;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting average sales price", e);
            return null;
        }
    }
    
    /**
     * Get all sale records for a player.
     * 
     * @param playerId The player ID
     * @param rarity The rarity of the card
     * @return List of sales prices
     */
    public List<BigDecimal> getPlayerSalesHistory(String playerId, String rarity) {
        String sql = "SELECT sale_price FROM player_sales_history " +
                     "WHERE player_id = ? AND rarity = ? " +
                     "ORDER BY sale_timestamp DESC";
        
        List<BigDecimal> prices = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, playerId);
            pstmt.setString(2, rarity);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    prices.add(rs.getBigDecimal("sale_price"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting player sales history", e);
        }
        
        return prices;
    }
    
    /**
     * Add a new high-priority player to the file.
     * 
     * @param playerId The player ID to add
     * @param rarity The rarity to monitor
     * @return true if the player was added, false if already exists
     */
    public boolean addHighPriorityPlayer(String playerId, String rarity) {
        // Check if already in the map
        if (playerRarityMap.containsKey(playerId)) {
            return false;
        }
        
        // Add to the map
        playerRarityMap.put(playerId, rarity);
        
        // Add to the file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(playersFilePath, true))) {
            writer.newLine();
            writer.write(playerId + "," + rarity);
            
            LOGGER.info("Added high-priority player: " + playerId + " (" + rarity + ")");
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error adding high-priority player to file", e);
            // Remove from map to maintain consistency
            playerRarityMap.remove(playerId);
            return false;
        }
    }
    
    /**
     * Remove a high-priority player from the file.
     * 
     * @param playerId The player ID to remove
     * @return true if the player was removed, false if not found
     */
    public boolean removeHighPriorityPlayer(String playerId) {
        // Check if in the map
        if (!playerRarityMap.containsKey(playerId)) {
            return false;
        }
        
        // Remove from the map
        String rarity = playerRarityMap.remove(playerId);
        
        // Rewrite the file without this player
        File file = new File(playersFilePath);
        File tempFile = new File(playersFilePath + ".tmp");
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Keep comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    writer.write(line);
                    writer.newLine();
                    continue;
                }
                
                // Skip the player we're removing
                String[] parts = line.split(",");
                if (parts.length >= 2 && parts[0].trim().equals(playerId)) {
                    continue;
                }
                
                // Keep other players
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error removing high-priority player from file", e);
            // Add back to map to maintain consistency
            playerRarityMap.put(playerId, rarity);
            return false;
        }
        
        // Replace original file with temp file
        if (!tempFile.renameTo(file)) {
            LOGGER.severe("Failed to rename temp file");
            // Add back to map to maintain consistency
            playerRarityMap.put(playerId, rarity);
            return false;
        }
        
        LOGGER.info("Removed high-priority player: " + playerId);
        return true;
    }
}