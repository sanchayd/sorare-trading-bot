package com.sorarebot.api;

import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.api.Error;
import com.apollographql.apollo.exception.ApolloException;
import com.sorarebot.model.Card;
import com.sorarebot.model.Player;
import com.sorarebot.model.Offer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.web3j.crypto.Credentials;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Client for interacting with Sorare's GraphQL API.
 * Handles marketplace data queries and transactions.
 */
public class SorareApiClient {
    private static final String SORARE_API_URL = "https://api.sorare.com/graphql";
    private static final String SORARE_AUTH_TOKEN_HEADER = "Authorization";
    
    private final ApolloClient apolloClient;
    private final Credentials credentials;
    private final String authToken;
    
    public SorareApiClient(String authToken, Credentials credentials) {
        this.authToken = authToken;
        this.credentials = credentials;
        
        OkHttpClient httpClient = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                Request original = chain.request();
                Request.Builder builder = original.newBuilder()
                        .header(SORARE_AUTH_TOKEN_HEADER, "Bearer " + authToken);
                Request request = builder.build();
                return chain.proceed(request);
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
            
        this.apolloClient = ApolloClient.builder()
            .serverUrl(SORARE_API_URL)
            .okHttpClient(httpClient)
            .build();
    }
    
    /**
     * Query the current market listings for a specific player.
     * 
     * @param playerId The Sorare ID of the player to query
     * @return List of available cards for the player
     * @throws IOException If there is an error communicating with the API
     */
    public List<Card> getPlayerListings(String playerId) throws IOException {
        try {
            // Define the GraphQL query for player listings
            PlayerCardsQuery query = PlayerCardsQuery.builder()
                .playerId(playerId)
                .build();
                
            Response<PlayerCardsQuery.Data> response = apolloClient.query(query).execute();
            
            if (response.hasErrors()) {
                throw new IOException("GraphQL errors: " + response.getErrors());
            }
            
            // Transform the response into our model objects
            return transformPlayerListingsResponse(response.getData());
        } catch (ApolloException e) {
            throw new IOException("Failed to query player listings", e);
        }
    }
    
    /**
     * Get the current floor price for a specific player card.
     * 
     * @param playerId The Sorare ID of the player
     * @param rarity The rarity of the card (limited, rare, super_rare, unique)
     * @return The floor price in ETH
     * @throws IOException If there is an error communicating with the API
     */
    public BigDecimal getFloorPrice(String playerId, String rarity) throws IOException {
        try {
            FloorPriceQuery query = FloorPriceQuery.builder()
                .playerId(playerId)
                .rarity(rarity)
                .build();
                
            Response<FloorPriceQuery.Data> response = apolloClient.query(query).execute();
            
            if (response.hasErrors()) {
                throw new IOException("GraphQL errors: " + response.getErrors());
            }
            
            // Extract the floor price from the response
            return extractFloorPrice(response.getData());
        } catch (ApolloException e) {
            throw new IOException("Failed to query floor price", e);
        }
    }
    
    /**
     * Submit a buy offer for a card.
     * 
     * @param cardId The Sorare ID of the card to buy
     * @param priceEth The price to offer in ETH
     * @return The transaction hash if successful
     * @throws IOException If there is an error with the transaction
     */
    public String submitBuyOffer(String cardId, BigDecimal priceEth) throws IOException {
        try {
            BuyOfferMutation mutation = BuyOfferMutation.builder()
                .cardId(cardId)
                .price(priceEth.toString())
                .build();
                
            Response<BuyOfferMutation.Data> response = apolloClient.mutate(mutation).execute();
            
            if (response.hasErrors()) {
                throw new IOException("GraphQL errors: " + response.getErrors());
            }
            
            return response.getData().buyNow().transactionHash();
        } catch (ApolloException e) {
            throw new IOException("Failed to submit buy offer", e);
        }
    }
    
    /**
     * List a card for sale.
     * 
     * @param cardId The Sorare ID of the card to list
     * @param priceEth The listing price in ETH
     * @return The listing ID if successful
     * @throws IOException If there is an error with the listing
     */
    public String listCardForSale(String cardId, BigDecimal priceEth) throws IOException {
        try {
            ListCardMutation mutation = ListCardMutation.builder()
                .cardId(cardId)
                .price(priceEth.toString())
                .build();
                
            Response<ListCardMutation.Data> response = apolloClient.mutate(mutation).execute();
            
            if (response.hasErrors()) {
                throw new IOException("GraphQL errors: " + response.getErrors());
            }
            
            return response.getData().createListing().id();
        } catch (ApolloException e) {
            throw new IOException("Failed to list card for sale", e);
        }
    }
    
    /**
     * Accept a counter-offer for a card.
     * 
     * @param offerId The ID of the offer to accept
     * @return The transaction hash if successful
     * @throws IOException If there is an error with the transaction
     */
    public String acceptOffer(String offerId) throws IOException {
        try {
            AcceptOfferMutation mutation = AcceptOfferMutation.builder()
                .offerId(offerId)
                .build();
                
            Response<AcceptOfferMutation.Data> response = apolloClient.mutate(mutation).execute();
            
            if (response.hasErrors()) {
                throw new IOException("GraphQL errors: " + response.getErrors());
            }
            
            return response.getData().acceptOffer().transactionHash();
        } catch (ApolloException e) {
            throw new IOException("Failed to accept offer", e);
        }
    }
    
    /**
     * Get all offers received for cards owned by the user.
     * 
     * @return List of active offers
     * @throws IOException If there is an error communicating with the API
     */
    public List<Offer> getReceivedOffers() throws IOException {
        try {
            ReceivedOffersQuery query = ReceivedOffersQuery.builder().build();
                
            Response<ReceivedOffersQuery.Data> response = apolloClient.query(query).execute();
            
            if (response.hasErrors()) {
                throw new IOException("GraphQL errors: " + response.getErrors());
            }
            
            return transformReceivedOffersResponse(response.getData());
        } catch (ApolloException e) {
            throw new IOException("Failed to query received offers", e);
        }
    }
    
    // Helper methods to transform GraphQL responses to model objects
    private List<Card> transformPlayerListingsResponse(PlayerCardsQuery.Data data) {
        // Implementation omitted for brevity
        // This would parse the GraphQL response into Card objects
        return List.of();
    }
    
    private BigDecimal extractFloorPrice(FloorPriceQuery.Data data) {
        // Implementation omitted for brevity
        // This would extract the floor price from the response
        return BigDecimal.ZERO;
    }
    
    private List<Offer> transformReceivedOffersResponse(ReceivedOffersQuery.Data data) {
        // Implementation omitted for brevity
        // This would parse the GraphQL response into Offer objects
        return List.of();
    }
}
