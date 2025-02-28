package com.sorarebot.blockchain;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Transfer;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Convert;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for interacting directly with the Ethereum blockchain.
 * Handles transaction verification and direct blockchain operations.
 */
public class BlockchainService {
    private static final Logger LOGGER = Logger.getLogger(BlockchainService.class.getName());
    
    private final Web3j web3j;
    private final Credentials credentials;
    
    /**
     * Contract address for Sorare's main marketplace contract.
     * This is used for direct contract interaction if needed.
     */
    private static final String SORARE_CONTRACT_ADDRESS = "0x629A673A8242c2AC4B7B8C5D8735fbeac21A6205";
    
    public BlockchainService(String ethereumNodeUrl, Credentials credentials) {
        this.web3j = Web3j.build(new HttpService(ethereumNodeUrl));
        this.credentials = credentials;
    }
    
    /**
     * Get the current ETH balance of the wallet.
     * 
     * @return The current balance in ETH
     * @throws IOException If there is an error communicating with the blockchain
     */
    public BigDecimal getEthBalance() throws IOException {
        try {
            EthGetBalance balanceWei = web3j.ethGetBalance(
                    credentials.getAddress(), DefaultBlockParameterName.LATEST).send();
            
            // Convert from Wei to ETH
            return Convert.fromWei(
                    new BigDecimal(balanceWei.getBalance()), Convert.Unit.ETHER);
        } catch (Exception e) {
            throw new IOException("Failed to get ETH balance", e);
        }
    }
    
    /**
     * Verify if a transaction has been confirmed on the blockchain.
     * 
     * @param transactionHash The hash of the transaction to verify
     * @return true if the transaction is confirmed, false otherwise
     */
    public boolean verifyTransaction(String transactionHash) {
        try {
            Optional<TransactionReceipt> receipt = web3j.ethGetTransactionReceipt(transactionHash)
                    .send().getTransactionReceipt();
            
            if (receipt.isPresent()) {
                // Check if the transaction was successful (status = 1)
                return receipt.get().isStatusOK();
            }
            
            // Transaction not found or not yet mined
            return false;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error verifying transaction: " + transactionHash, e);
            return false;
        }
    }
    
    /**
     * Send ETH to another address (emergency function).
     * 
     * @param toAddress The address to send ETH to
     * @param amount The amount of ETH to send
     * @return The transaction hash if successful
     * @throws Exception If there is an error with the transaction
     */
    public String transferEth(String toAddress, BigDecimal amount) throws Exception {
        try {
            TransactionReceipt receipt = Transfer.sendFunds(
                    web3j, credentials, toAddress,
                    amount, Convert.Unit.ETHER).send();
            
            return receipt.getTransactionHash();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error transferring ETH to: " + toAddress, e);
            throw e;
        }
    }
    
    /**
     * Check if a transaction requires additional gas due to network congestion.
     * This is useful for high-priority transactions during market volatility.
     * 
     * @param transactionHash The hash of the transaction to check
     * @return true if the transaction needs more gas, false otherwise
     */
    public boolean needsMoreGas(String transactionHash) {
        try {
            // Get the current gas price
            BigInteger currentGasPrice = web3j.ethGasPrice().send().getGasPrice();
            
            // Get the transaction's gas price
            BigInteger txGasPrice = web3j.ethGetTransactionByHash(transactionHash)
                    .send().getTransaction()
                    .map(tx -> tx.getGasPrice())
                    .orElse(BigInteger.ZERO);
            
            // If the current gas price is significantly higher, the transaction may be stuck
            return currentGasPrice.compareTo(txGasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN)) > 0;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error checking gas price for transaction: " + transactionHash, e);
            return false;
        }
    }
    
    /**
     * Create a direct contract instance for the Sorare marketplace.
     * This allows for direct interaction with the contract if the API is unavailable.
     * 
     * @return The contract instance
     */
    public SorareMarketplace getSorareContract() {
        return SorareMarketplace.load(
                SORARE_CONTRACT_ADDRESS,
                web3j,
                credentials,
                new DefaultGasProvider());
    }
    
    /**
     * Close connections and free resources.
     */
    public void shutdown() {
        web3j.shutdown();
        LOGGER.info("Blockchain service shut down");
    }
}
