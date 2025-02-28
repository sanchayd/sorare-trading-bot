package com.sorarebot.model;

/**
 * Represents a Sorare player that can be traded.
 */
public class Player {
    private final String id;
    private final String name;
    private final String rarity;
    
    public Player(String id, String name, String rarity) {
        this.id = id;
        this.name = name;
        this.rarity = rarity;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getRarity() {
        return rarity;
    }
    
    @Override
    public String toString() {
        return "Player{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", rarity='" + rarity + '\'' +
                '}';
    }
}
