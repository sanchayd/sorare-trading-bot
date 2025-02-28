package com.sorarebot.blockchain;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

/**
 * Simplified wrapper for Sorare's marketplace contract.
 */
public class SorareMarketplace extends Contract {
    
    protected SorareMarketplace(String contractAddress, Web3j web3j, Credentials credentials, 
                              ContractGasProvider gasProvider) {
        super("", contractAddress, web3j, credentials, gasProvider);
    }
    
    /**
     * Load an instance of the contract.
     */
    public static SorareMarketplace load(String contractAddress, Web3j web3j, 
                                       Credentials credentials, ContractGasProvider gasProvider) {
        return new SorareMarketplace(contractAddress, web3j, credentials, gasProvider);
    }
    
    /**
     * Buy a card directly using ETH.
     */
    public RemoteCall<TransactionReceipt> buyCard(String cardId, BigInteger priceWei) {
        Function function = new Function(
                "buy",
                Arrays.asList(new Address(cardId), new Uint256(priceWei)),
                Collections.emptyList());
        return executeRemoteCallTransaction(function, priceWei);
    }
    
    /**
     * Create an offer for a card.
     */
    public RemoteCall<TransactionReceipt> makeOffer(String cardId, BigInteger priceWei, BigInteger expiryTime) {
        Function function = new Function(
                "makeOffer",
                Arrays.asList(new Address(cardId), new Uint256(priceWei), new Uint256(expiryTime)),
                Collections.emptyList());
        return executeRemoteCallTransaction(function, priceWei);
    }
    
    /**
     * Accept an offer for a card.
     */
    public RemoteCall<TransactionReceipt> acceptOffer(String offerId) {
        Function function = new Function(
                "acceptOffer",
                Arrays.asList(new Uint256(new BigInteger(offerId))),
                Collections.emptyList());
        return executeRemoteCallTransaction(function);
    }
    
    /**
     * List a card for sale.
     */
    public RemoteCall<TransactionReceipt> listCard(String cardId, BigInteger priceWei) {
        Function function = new Function(
                "createAuction",
                Arrays.asList(new Address(cardId), new Uint256(priceWei)),
                Collections.emptyList());
        return executeRemoteCallTransaction(function);
    }
}
