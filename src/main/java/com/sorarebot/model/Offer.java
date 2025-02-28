package com.sorarebot.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents an offer made for a Sorare card.
 */
public class Offer {
    private final String id;
    private final String cardId;
    private final String buyerId;
    private final BigDecimal price;
    private final Instant createdAt;
    private final Instant expiresAt;
    
    public Offer(String id, String cardId, String buyerId, BigDecimal price, 
                Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.cardId = cardId;
        this.buyerId = buyerId;
        this.price = price;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
    
    public String getId() {
        return id;
    }
    
    public String getCardId() {
        return cardId;
    }
    
    public String getBuyerId() {
        return buyerId;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getExpiresAt() {
        return expiresAt;
    }
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    @Override
    public String toString() {
        return "Offer{" +
                "id='" + id + '\'' +
                ", cardId='" + cardId + '\'' +
                ", buyerId='" + buyerId + '\'' +
                ", price=" + price +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
