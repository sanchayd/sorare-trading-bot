package com.sorarebot.security;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enforces transaction and spending limits to protect against financial loss.
 * Implements various controls like rate limits, spending limits,
 * and transaction size limits with approval policies.
 */
public class SpendingControls {
    private static final Logger LOGGER = Logger.getLogger(SpendingControls.class.getName());
    
    // Transaction history for calculating spending limits
    private final ConcurrentLinkedQueue<Transaction> recentTransactions = new ConcurrentLinkedQueue<>();
    
    // Spending limits
    private final BigDecimal maxSingleTransactionEth;
    private final BigDecimal maxDailySpendingEth;
    private final BigDecimal maxWeeklySpendingEth;
    private final int maxTransactionsPerHour;
    
    // High value transaction threshold and approval callback 
    private final BigDecimal highValueThresholdEth;
    private final TransactionApprovalPolicy approvalPolicy;
    
    // Emergency stop for serious issues
    private final EmergencyStop emergencyStop;
    
    // Remaining budget tracking
    private final AtomicReference<BigDecimal> remainingDailyBudget;
    private final AtomicReference<BigDecimal> remainingWeeklyBudget;
    
    /**
     * Create a new spending controls instance with specified limits.
     * 
     * @param maxSingleTransactionEth Maximum size of a single transaction
     * @param maxDailySpendingEth Maximum daily spending
     * @param maxWeeklySpendingEth Maximum weekly spending
     * @param maxTransactionsPerHour Maximum number of transactions per hour
     * @param highValueThresholdEth Threshold for high-value transactions requiring approval
     * @param approvalPolicy Policy for approving transactions
     * @param emergencyStop Emergency stop mechanism
     */
    public SpendingControls(BigDecimal maxSingleTransactionEth, 
                          BigDecimal maxDailySpendingEth,
                          BigDecimal maxWeeklySpendingEth,
                          int maxTransactionsPerHour,
                          BigDecimal highValueThresholdEth,
                          TransactionApprovalPolicy approvalPolicy,
                          EmergencyStop emergencyStop) {
        this.maxSingleTransactionEth = maxSingleTransactionEth;
        this.maxDailySpendingEth = maxDailySpendingEth;
        this.maxWeeklySpendingEth = maxWeeklySpendingEth;
        this.maxTransactionsPerHour = maxTransactionsPerHour;
        this.highValueThresholdEth = highValueThresholdEth;
        this.approvalPolicy = approvalPolicy;
        this.emergencyStop = emergencyStop;
        
        // Initialize remaining budgets
        this.remainingDailyBudget = new AtomicReference<>(maxDailySpendingEth);
        this.remainingWeeklyBudget = new AtomicReference<>(maxWeeklySpendingEth);
        
        // Log initialization
        LOGGER.info("Spending controls initialized:");
        LOGGER.info("- Max single transaction: " + maxSingleTransactionEth + " ETH");
        LOGGER.info("- Max daily spending: " + maxDailySpendingEth + " ETH");
        LOGGER.info("- Max weekly spending: " + maxWeeklySpendingEth + " ETH");
        LOGGER.info("- Max transactions per hour: " + maxTransactionsPerHour);
        LOGGER.info("- High value threshold: " + highValueThresholdEth + " ETH");
    }
    
    /**
     * Check if a transaction can be executed based on all spending controls.
     * 
     * @param transactionId A unique identifier for the transaction
     * @param amountEth The amount of ETH to spend
     * @param description A description of the transaction
     * @return true if the transaction can be executed, false otherwise
     */
    public boolean canExecuteTransaction(String transactionId, BigDecimal amountEth, String description) {
        // Check if emergency stop is active
        if (emergencyStop.isEmergencyStopActive()) {
            LOGGER.severe("Transaction rejected - Emergency stop is active");
            return false;
        }
        
        LOGGER.info("Evaluating transaction: " + transactionId + " - " + 
                   amountEth + " ETH - " + description);
        
        // Single transaction size limit check
        if (amountEth.compareTo(maxSingleTransactionEth) > 0) {
            LOGGER.warning("Transaction rejected - Exceeds maximum transaction size: " + 
                          amountEth + " ETH > " + maxSingleTransactionEth + " ETH");
            return false;
        }
        
        // Update transaction history and spending calculations
        updateTransactionHistory();
        
        // Rate limit check
        int transactionsInLastHour = countRecentTransactions(Duration.ofHours(1));
        if (transactionsInLastHour >= maxTransactionsPerHour) {
            LOGGER.warning("Transaction rejected - Exceeds transaction rate limit: " + 
                          transactionsInLastHour + " transactions in the last hour");
            return false;
        }
        
        // Spending limit checks
        BigDecimal spentToday = calculateSpending(Duration.ofDays(1));
        BigDecimal spentThisWeek = calculateSpending(Duration.ofDays(7));
        
        // Update remaining budgets
        remainingDailyBudget.set(maxDailySpendingEth.subtract(spentToday).max(BigDecimal.ZERO));
        remainingWeeklyBudget.set(maxWeeklySpendingEth.subtract(spentThisWeek).max(BigDecimal.ZERO));
        
        // Check if this transaction would exceed daily spending limit
        if (spentToday.add(amountEth).compareTo(maxDailySpendingEth) > 0) {
            LOGGER.warning("Transaction rejected - Would exceed daily spending limit: " + 
                          spentToday + " + " + amountEth + " > " + maxDailySpendingEth + " ETH");
            return false;
        }
        
        // Check if this transaction would exceed weekly spending limit
        if (spentThisWeek.add(amountEth).compareTo(maxWeeklySpendingEth) > 0) {
            LOGGER.warning("Transaction rejected - Would exceed weekly spending limit: " + 
                          spentThisWeek + " + " + amountEth + " > " + maxWeeklySpendingEth + " ETH");
            return false;
        }
        
        // High value transaction approval check
        if (amountEth.compareTo(highValueThresholdEth) >= 0) {
            LOGGER.info("High value transaction detected: " + amountEth + " ETH - Requesting approval");
            
            if (approvalPolicy != null) {
                boolean approved = approvalPolicy.approveTransaction(
                    transactionId, amountEth, description, remainingDailyBudget.get(), remainingWeeklyBudget.get());
                
                if (!approved) {
                    LOGGER.warning("Transaction rejected - High value transaction not approved");
                    return false;
                }
                
                LOGGER.info("High value transaction approved");
            } else {
                LOGGER.warning("No approval policy defined for high value transactions");
                // If no approval policy is defined, we'll allow the transaction
                // but log a warning
            }
        }
        
        // All checks passed
        LOGGER.info("Transaction approved: " + transactionId + " - " + amountEth + " ETH");
        return true;
    }
    
    /**
     * Record a completed transaction.
     * 
     * @param transactionId A unique identifier for the transaction
     * @param amountEth The amount of ETH spent
     * @param description A description of the transaction
     */
    public void recordTransaction(String transactionId, BigDecimal amountEth, String description) {
        Transaction transaction = new Transaction(
            transactionId, amountEth, Instant.now(), description);
        
        recentTransactions.add(transaction);
        
        // Update remaining budgets
        updateTransactionHistory();
        BigDecimal spentToday = calculateSpending(Duration.ofDays(1));
        BigDecimal spentThisWeek = calculateSpending(Duration.ofDays(7));
        
        remainingDailyBudget.set(maxDailySpendingEth.subtract(spentToday).max(BigDecimal.ZERO));
        remainingWeeklyBudget.set(maxWeeklySpendingEth.subtract(spentThisWeek).max(BigDecimal.ZERO));
        
        LOGGER.info("Transaction recorded: " + transactionId + " - " + amountEth + " ETH");
        LOGGER.info("Remaining daily budget: " + remainingDailyBudget.get() + " ETH");
        LOGGER.info("Remaining weekly budget: " + remainingWeeklyBudget.get() + " ETH");
    }
    
    /**
     * Get the remaining daily spending budget.
     * 
     * @return The remaining daily budget in ETH
     */
    public BigDecimal getRemainingDailyBudget() {
        updateTransactionHistory();
        return remainingDailyBudget.get();
    }
    
    /**
     * Get the remaining weekly spending budget.
     * 
     * @return The remaining weekly budget in ETH
     */
    public BigDecimal getRemainingWeeklyBudget() {
        updateTransactionHistory();
        return remainingWeeklyBudget.get();
    }
    
    /**
     * Get the transaction count in the last hour.
     * 
     * @return The number of transactions in the last hour
     */
    public int getTransactionCountLastHour() {
        updateTransactionHistory();
        return countRecentTransactions(Duration.ofHours(1));
    }
    
    /**
     * Get recent transactions from the specified time period.
     * 
     * @param duration The time period to get transactions from
     * @return A list of recent transactions
     */
    public List<Transaction> getRecentTransactions(Duration duration) {
        updateTransactionHistory();
        Instant cutoff = Instant.now().minus(duration);
        
        List<Transaction> result = new ArrayList<>();
        for (Transaction transaction : recentTransactions) {
            if (transaction.timestamp.isAfter(cutoff)) {
                result.add(transaction);
            }
        }
        
        return result;
    }
    
    /**
     * Get the amount spent in a time period.
     * 
     * @param duration The time period to calculate spending for
     * @return The total amount spent in ETH
     */
    public BigDecimal getAmountSpent(Duration duration) {
        updateTransactionHistory();
        return calculateSpending(duration);
    }
    
    /**
     * Update the transaction history by removing old transactions.
     */
    private void updateTransactionHistory() {
        // Keep transactions for at least a week for reporting
        Instant oneWeekAgo = Instant.now().minus(Duration.ofDays(7));
        
        // Remove old transactions
        recentTransactions.removeIf(tx -> tx.timestamp.isBefore(oneWeekAgo));
    }
    
    /**
     * Count the number of transactions in a recent time period.
     * 
     * @param duration The time period to count transactions in
     * @return The number of transactions in the time period
     */
    private int countRecentTransactions(Duration duration) {
        Instant cutoff = Instant.now().minus(duration);
        return (int) recentTransactions.stream()
            .filter(tx -> tx.timestamp.isAfter(cutoff))
            .count();
    }
    
    /**
     * Calculate the total spending in a time period.
     * 
     * @param duration The time period to calculate spending for
     * @return The total amount spent in ETH
     */
    private BigDecimal calculateSpending(Duration duration) {
        Instant cutoff = Instant.now().minus(duration);
        
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction transaction : recentTransactions) {
            if (transaction.timestamp.isAfter(cutoff)) {
                total = total.add(transaction.amountEth);
            }
        }
        
        return total.setScale(8, RoundingMode.HALF_UP);
    }
    
    /**
     * Transaction record for tracking spending.
     */
    public static class Transaction {
        private final String id;
        private final BigDecimal amountEth;
        private final Instant timestamp;
        private final String description;
        
        public Transaction(String id, BigDecimal amountEth, Instant timestamp, String description) {
            this.id = id;
            this.amountEth = amountEth;
            this.timestamp = timestamp;
            this.description = description;
        }
        
        public String getId() {
            return id;
        }
        
        public BigDecimal getAmountEth() {
            return amountEth;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
        
        public String getDescription() {
            return description;
        }
        
        @Override
        public String toString() {
            return "Transaction{" +
                   "id='" + id + '\'' +
                   ", amount=" + amountEth + " ETH" +
                   ", timestamp=" + timestamp +
                   ", description='" + description + '\'' +
                   '}';
        }
    }
    
    /**
     * Interface for transaction approval policies.
     */
    public interface TransactionApprovalPolicy {
        /**
         * Approve a high-value transaction.
         * 
         * @param transactionId The transaction ID
         * @param amountEth The amount of ETH
         * @param description The transaction description
         * @param remainingDailyBudget The remaining daily budget
         * @param remainingWeeklyBudget The remaining weekly budget
         * @return true if approved, false otherwise
         */
        boolean approveTransaction(String transactionId, BigDecimal amountEth, 
                                   String description, BigDecimal remainingDailyBudget,
                                   BigDecimal remainingWeeklyBudget);
    }
    
    /**
     * Implementation of approval policy that sends an email and waits for confirmation.
     */
    public static class EmailApprovalPolicy implements TransactionApprovalPolicy {
        private final EmailService emailService;
        private final String approverEmail;
        private final long approvalTimeoutMs;
        
        public EmailApprovalPolicy(EmailService emailService, String approverEmail, long approvalTimeoutMs) {
            this.emailService = emailService;
            this.approverEmail = approverEmail;
            this.approvalTimeoutMs = approvalTimeoutMs;
        }
        
        @Override
        public boolean approveTransaction(String transactionId, BigDecimal amountEth,
                                         String description, BigDecimal remainingDailyBudget,
                                         BigDecimal remainingWeeklyBudget) {
            try {
                // Generate an approval code
                String approvalCode = generateApprovalCode();
                
                // Send approval request email
                String subject = "HIGH VALUE TRANSACTION APPROVAL REQUIRED: " + amountEth + " ETH";
                String body = String.format(
                    "A high value transaction requires your approval:\n\n" +
                    "Transaction ID: %s\n" +
                    "Amount: %s ETH\n" +
                    "Description: %s\n\n" +
                    "Budget Information:\n" +
                    "- Remaining daily budget: %s ETH\n" +
                    "- Remaining weekly budget: %s ETH\n\n" +
                    "To approve this transaction, reply with the code: %s\n\n" +
                    "This code will expire in %d minutes.\n\n" +
                    "If you did not initiate this transaction, please take immediate action to secure your bot.",
                    transactionId, amountEth, description, 
                    remainingDailyBudget, remainingWeeklyBudget,
                    approvalCode, approvalTimeoutMs / 60000
                );
                
                emailService.sendEmail(approverEmail, subject, body);
                
                LOGGER.info("Approval request sent to: " + approverEmail);
                
                // In a real implementation, we would wait for an approval from a response system
                // For this demonstration, we'll simulate waiting for approval
                LOGGER.info("Waiting for approval (code: " + approvalCode + ")...");
                
                // In a real system, you would implement a way to wait for and check approval
                // This is a simplified version that always returns false
                return false;
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error requesting transaction approval", e);
                return false;
            }
        }
        
        private String generateApprovalCode() {
            // Generate a 6-digit approval code
            int code = 100000 + new SecureRandom().nextInt(900000);
            return String.valueOf(code);
        }
    }
    
    /**
     * Simple interface for email service.
     */
    public interface EmailService {
        void sendEmail(String to, String subject, String body) throws Exception;
    }
    
    /**
     * Secure random number generator.
     */
    private static class SecureRandom extends java.security.SecureRandom {
        private static final long serialVersionUID = 1L;
    }
}