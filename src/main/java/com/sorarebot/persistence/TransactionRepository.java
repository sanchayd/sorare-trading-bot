package com.sorarebot.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for managing transaction history in a SQLite database.
 */
public class TransactionRepository {
    private static final Logger LOGGER = Logger.getLogger(TransactionRepository.class.getName());
    
    private final String dbUrl;
    
    public TransactionRepository(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        
        // Initialize the database and create tables if they don't exist
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            String createTableSQL = 
                "CREATE TABLE IF NOT EXISTS transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "card_id TEXT NOT NULL," +
                "type TEXT NOT NULL," +  // 'PURCHASE', 'LISTING', 'SALE'
                "price DECIMAL(18,6) NOT NULL," +
                "tx_hash TEXT," +
                "listing_id TEXT," +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
                
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSQL);
            }
            
            LOGGER.info("Transaction database initialized");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error initializing transaction database", e);
        }
    }
    
    /**
     * Record a card purchase.
     * 
     * @param cardId The ID of the card purchased
     * @param price The purchase price in ETH
     * @param txHash The transaction hash
     */
    public void recordPurchase(String cardId, BigDecimal price, String txHash) {
        String sql = "INSERT INTO transactions (card_id, type, price, tx_hash) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, cardId);
            pstmt.setString(2, "PURCHASE");
            pstmt.setBigDecimal(3, price);
            pstmt.setString(4, txHash);
            
            pstmt.executeUpdate();
            
            LOGGER.info("Recorded purchase: " + cardId + " for " + price + " ETH");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error recording purchase", e);
        }
    }
    
    /**
     * Record a card listing.
     * 
     * @param cardId The ID of the card listed
     * @param price The listing price in ETH
     * @param listingId The listing ID
     */
    public void recordListing(String cardId, BigDecimal price, String listingId) {
        String sql = "INSERT INTO transactions (card_id, type, price, listing_id) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, cardId);
            pstmt.setString(2, "LISTING");
            pstmt.setBigDecimal(3, price);
            pstmt.setString(4, listingId);
            
            pstmt.executeUpdate();
            
            LOGGER.info("Recorded listing: " + cardId + " for " + price + " ETH");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error recording listing", e);
        }
    }
    
    /**
     * Record a card sale.
     * 
     * @param cardId The ID of the card sold
     * @param price The sale price in ETH
     * @param txHash The transaction hash
     */
    public void recordSale(String cardId, BigDecimal price, String txHash) {
        String sql = "INSERT INTO transactions (card_id, type, price, tx_hash) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, cardId);
            pstmt.setString(2, "SALE");
            pstmt.setBigDecimal(3, price);
            pstmt.setString(4, txHash);
            
            pstmt.executeUpdate();
            
            LOGGER.info("Recorded sale: " + cardId + " for " + price + " ETH");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error recording sale", e);
        }
    }
    
    /**
     * Get the purchase price for a card.
     * 
     * @param cardId The ID of the card
     * @return The purchase price in ETH, or null if not found
     */
    public BigDecimal getPurchasePrice(String cardId) {
        String sql = "SELECT price FROM transactions WHERE card_id = ? AND type = 'PURCHASE' ORDER BY timestamp DESC LIMIT 1";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, cardId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("price");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving purchase price", e);
        }
        
        return null;
    }
    
    /**
     * Get recent transactions.
     * 
     * @param limit Maximum number of transactions to return
     * @return List of recent transactions
     */
    public List<String> getRecentTransactions(int limit) {
        List<String> transactions = new ArrayList<>();
        String sql = "SELECT card_id, type, price, tx_hash, listing_id, timestamp FROM transactions ORDER BY timestamp DESC LIMIT ?";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, limit);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String cardId = rs.getString("card_id");
                    String type = rs.getString("type");
                    BigDecimal price = rs.getBigDecimal("price");
                    Timestamp timestamp = rs.getTimestamp("timestamp");
                    
                    String formattedTx = String.format("[%s] %s %s: %s ETH", 
                            timestamp, type, cardId, price.toPlainString());
                    transactions.add(formattedTx);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving recent transactions", e);
        }
        
        return transactions;
    }
}
