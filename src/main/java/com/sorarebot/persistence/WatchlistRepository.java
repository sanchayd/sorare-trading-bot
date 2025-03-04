package com.sorarebot.persistence;

import com.sorarebot.model.Player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for managing the player watchlist in a SQLite database.
 */
public class WatchlistRepository {
    private static final Logger LOGGER = Logger.getLogger(WatchlistRepository.class.getName());
    
    private final String dbUrl;
    
    public WatchlistRepository(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        
        // Initialize the database and create tables if they don't exist
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            String createTableSQL = 
                "CREATE TABLE IF NOT EXISTS watchlist (" +
                "player_id TEXT PRIMARY KEY," +
                "name TEXT," +
                "rarity TEXT NOT NULL," +
                "added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
                
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSQL);
            }
            
            LOGGER.info("Watchlist database initialized");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error initializing watchlist database", e);
        }
    }
    
    /**
     * Add a player to the watchlist.
     * 
     * @param player The player to add
     */
    public void addToWatchlist(Player player) {
        String sql = "INSERT OR REPLACE INTO watchlist (player_id, name, rarity) VALUES (?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, player.getId());
            pstmt.setString(2, player.getName());
            pstmt.setString(3, player.getRarity());
            
            pstmt.executeUpdate();
            
            LOGGER.info("Added player to watchlist: " + player.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error adding player to watchlist", e);
        }
    }
    
    /**
     * Remove a player from the watchlist.
     * 
     * @param playerId The ID of the player to remove
     */
    public void removeFromWatchlist(String playerId) {
        String sql = "DELETE FROM watchlist WHERE player_id = ?";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, playerId);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                LOGGER.info("Removed player from watchlist: " + playerId);
            } else {
                LOGGER.warning("Player not found in watchlist: " + playerId);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error removing player from watchlist", e);
        }
    }
    
    /**
     * Get all players in the watchlist.
     * 
     * @return List of players in the watchlist
     */
    public List<Player> getWatchlist() {
        List<Player> watchlist = new ArrayList<>();
        String sql = "SELECT player_id, name, rarity FROM watchlist";
        
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String playerId = rs.getString("player_id");
                String name = rs.getString("name");
                String rarity = rs.getString("rarity");
                
                watchlist.add(new Player(playerId, name, rarity));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving watchlist", e);
        }
        
        return watchlist;
    }
}
