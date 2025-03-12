package com.sorarebot.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for managing special card preferences in a SQLite database.
 */
public class CardPreferenceRepository {
    private static final Logger LOGGER = Logger.getLogger(CardPreferenceRepository.class.getName());
    
    private final String dbUrl;
    
    public CardPreferenceRepository(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        
        // Initialize the database and create tables if they don't exist
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            // Create table for favorite serial numbers
            String createSerialTableSQL = 
                "CREATE TABLE IF NOT EXISTS favorite_serials (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "serial_number INTEGER NOT NULL," +
                "rarity TEXT," +  // Optional rarity filter
                "added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
                
            // Create table for jersey mint preferences
            String createJerseyMintTableSQL = 
                "CREATE TABLE IF NOT EXISTS jersey_mint_preferences (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "enabled BOOLEAN NOT NULL DEFAULT 1," +
                "max_price DECIMAL(18,6)," +  // Optional max price filter
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
                
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createSerialTableSQL);
                stmt.execute(createJerseyMintTableSQL);
                
                // Insert default value for jersey mint preferences if not exists
                stmt.execute(
                    "INSERT OR IGNORE INTO jersey_mint_preferences (id, enabled) " +
                    "SELECT 1, 1 WHERE NOT EXISTS (SELECT 1 FROM jersey_mint_preferences WHERE id = 1)"
                );
            }
            
            LOGGER.info("Card preference database initialized");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error initializing card preference database", e);
        }
    }
    
    /**
     * Add a favorite serial number.
     * 
     * @param serialNumber The serial number to add as a favorite
     * @param rarity Optional rarity filter (can be null)
     */
    public void addFavoriteSerial(int serialNumber, String rarity) {
        String sql = "INSERT INTO favorite_serials (serial_number, rarity) VALUES (?, ?)";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, serialNumber);
            if (rarity != null && !rarity.isEmpty()) {
                pstmt.setString(2, rarity);
            } else {
                pstmt.setNull(2, java.sql.Types.VARCHAR);
            }
            
            pstmt.executeUpdate();
            
            LOGGER.info("Added favorite serial number: " + serialNumber + 
                       (rarity != null ? " (Rarity: " + rarity + ")" : ""));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error adding favorite serial number", e);
        }
    }
    
    /**
     * Remove a favorite serial number.
     * 
     * @param serialNumber The serial number to remove
     * @param rarity Optional rarity filter (can be null)
     */
    public void removeFavoriteSerial(int serialNumber, String rarity) {
        String sql;
        if (rarity != null && !rarity.isEmpty()) {
            sql = "DELETE FROM favorite_serials WHERE serial_number = ? AND rarity = ?";
        } else {
            sql = "DELETE FROM favorite_serials WHERE serial_number = ? AND rarity IS NULL";
        }
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, serialNumber);
            if (rarity != null && !rarity.isEmpty()) {
                pstmt.setString(2, rarity);
            }
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                LOGGER.info("Removed favorite serial number: " + serialNumber);
            } else {
                LOGGER.warning("Serial number not found in favorites: " + serialNumber);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error removing favorite serial number", e);
        }
    }
    
    /**
     * Get all favorite serial numbers.
     * 
     * @return Set of favorite serial numbers
     */
    public Set<Integer> getFavoriteSerials() {
        Set<Integer> serialNumbers = new HashSet<>();
        String sql = "SELECT serial_number FROM favorite_serials";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                serialNumbers.add(rs.getInt("serial_number"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving favorite serial numbers", e);
        }
        
        return serialNumbers;
    }
    
    /**
     * Check if jersey mint notifications are enabled.
     * 
     * @return true if jersey mint notifications are enabled, false otherwise
     */
    public boolean isJerseyMintEnabled() {
        String sql = "SELECT enabled FROM jersey_mint_preferences WHERE id = 1";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getBoolean("enabled");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error checking jersey mint preferences", e);
        }
        
        return false; // Default to disabled if there's an error
    }
    
    /**
     * Enable or disable jersey mint notifications.
     * 
     * @param enabled true to enable, false to disable
     */
    public void setJerseyMintEnabled(boolean enabled) {
        String sql = "UPDATE jersey_mint_preferences SET enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setBoolean(1, enabled);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                LOGGER.info("Jersey mint notifications " + (enabled ? "enabled" : "disabled"));
            } else {
                LOGGER.warning("Failed to update jersey mint preferences");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating jersey mint preferences", e);
        }
    }
    
    /**
     * Set the maximum price for jersey mint notifications.
     * 
     * @param maxPrice The maximum price in ETH (null to remove the filter)
     */
    public void setJerseyMintMaxPrice(Double maxPrice) {
        String sql = "UPDATE jersey_mint_preferences SET max_price = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            if (maxPrice != null) {
                pstmt.setDouble(1, maxPrice);
            } else {
                pstmt.setNull(1, java.sql.Types.DECIMAL);
            }
            
            pstmt.executeUpdate();
            
            LOGGER.info("Set jersey mint max price to: " + 
                      (maxPrice != null ? maxPrice + " ETH" : "no limit"));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating jersey mint max price", e);
        }
    }
    
    /**
     * Get the maximum price for jersey mint notifications.
     * 
     * @return The maximum price in ETH, or null if no limit is set
     */
    public Double getJerseyMintMaxPrice() {
        String sql = "SELECT max_price FROM jersey_mint_preferences WHERE id = 1";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                double maxPrice = rs.getDouble("max_price");
                if (rs.wasNull()) {
                    return null;
                }
                return maxPrice;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving jersey mint max price", e);
        }
        
        return null;
    }
}