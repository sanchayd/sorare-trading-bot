package com.sorarebot.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a Sorare card that can be bought or sold.
 */
public class Card {
    private final String id;
    private final String playerId;
    private final String rarity;
    private final BigDecimal price;
    private final String seller;
    private final Instant listedAt;
    
    public Card(String id, String playerId, String rarity, BigDecimal price, 
                String seller, Instant listedAt) {
        this.id = id;
        this.playerId = playerId;
        this.rarity = rarity;
        this.price = price;
        this.seller = seller;
        this.listedAt = listedAt;
    }
    
    public String getId() {
        return id;
    }
    
    public String getPlayerId() {
        return playerId;
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
    
    @Override
    public String toString() {
        return "Card{" +
                "id='" + id + '\'' +
                ", playerId='" + playerId + '\'' +
                ", rarity='" + rarity + '\'' +
                ", price=" + price +
                ", seller='" + seller + '\'' +
                ", listedAt=" + listedAt +
                '}';
    }
}
