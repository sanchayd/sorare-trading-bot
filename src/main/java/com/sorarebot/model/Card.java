package com.sorarebot.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a Sorare card that can be bought or sold.
 */
public class Card {
    private final String id;
    private final String playerId;
    private final String playerName;
    private final String rarity;
    private final BigDecimal price;
    private final String seller;
    private final Instant listedAt;
    private final int serial;
    private final int serialCount;
    private final Integer playerJerseyNumber;
    
    public Card(String id, String playerId, String playerName, String rarity, 
                BigDecimal price, String seller, Instant listedAt,
                int serial, int serialCount, Integer playerJerseyNumber) {
        this.id = id;
        this.playerId = playerId;
        this.playerName = playerName;
        this.rarity = rarity;
        this.price = price;
        this.seller = seller;
        this.listedAt = listedAt;
        this.serial = serial;
        this.serialCount = serialCount;
        this.playerJerseyNumber = playerJerseyNumber;
    }
    
    public String getId() {
        return id;
    }
    
    public String getPlayerId() {
        return playerId;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public String getRarity() {
        return rarity;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public String getSeller() {
        return seller;
    }
    
    public Instant getListedAt() {
        return listedAt;
    }
    
    /**
     * Get the serial number of this card.
     * 
     * @return The serial number (e.g., 66 in 66/100)
     */
    public int getSerial() {
        return serial;
    }
    
    /**
     * Get the total number of cards in this series.
     * 
     * @return The serial count (e.g., 100 in 66/100)
     */
    public int getSerialCount() {
        return serialCount;
    }
    
    /**
     * Get the player's jersey number, if available.
     * 
     * @return The player's jersey number, or null if not available
     */
    public Integer getPlayerJerseyNumber() {
        return playerJerseyNumber;
    }
    
    /**
     * Check if this card is a jersey mint (serial number matches player's jersey number).
     * 
     * @return true if this is a jersey mint, false otherwise
     */
    public boolean isJerseyMint() {
        return playerJerseyNumber != null && serial == playerJerseyNumber;
    }
    
    /**
     * Get the serial number as a string in the format "X/Y".
     * 
     * @return The serial number string (e.g., "66/100")
     */
    public String getSerialString() {
        return serial + "/" + serialCount;
    }
    
    @Override
    public String toString() {
        return "Card{" +
                "id='" + id + '\'' +
                ", playerId='" + playerId + '\'' +
                ", playerName='" + playerName + '\'' +
                ", rarity='" + rarity + '\'' +
                ", serial=" + getSerialString() +
                (isJerseyMint() ? " (Jersey Mint)" : "") +
                ", price=" + price +
                ", seller='" + seller + '\'' +
                ", listedAt=" + listedAt +
                '}';
    }
}