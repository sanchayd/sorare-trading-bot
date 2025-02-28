package com.sorarebot.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for handling security-sensitive operations including key encryption and decryption.
 * Provides utilities for securely handling private keys and API tokens.
 */
public class SecurityService {
    private static final Logger LOGGER = Logger.getLogger(SecurityService.class.getName());
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final int SALT_LENGTH_BYTE = 16;
    private static final int AES_KEY_BIT = 256;
    
    private final SecureRandom secureRandom;
    private final String keyStorePath;
    
    public SecurityService(String keyStorePath) {
        this.secureRandom = new SecureRandom();
        this.keyStorePath = keyStorePath;
        
        // Ensure key store directory exists
        try {
            Path path = Paths.get(keyStorePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating key store directory", e);
        }
    }
    
    /**
     * Encrypt sensitive data using AES-GCM.
     * 
     * @param data The data to encrypt
     * @param password The password to derive the encryption key from
     * @return The encrypted data as a Base64 string
     * @throws Exception If encryption fails
     */
    public String encrypt(String data, String password) throws Exception {
        // Generate salt for key derivation
        byte[] salt = new byte[SALT_LENGTH_BYTE];
        secureRandom.nextBytes(salt);
        
        // Derive key from password and salt
        SecretKey key = getAESKeyFromPassword(password, salt);
        
        // Generate initialization vector
        byte[] iv = new byte[IV_LENGTH_BYTE];
        secureRandom.nextBytes(iv);
        
        // Initialize cipher for encryption
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
        
        // Encrypt the data
        byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        
        // Combine salt, IV, and encrypted data
        ByteBuffer byteBuffer = ByteBuffer.allocate(salt.length + iv.length + encryptedData.length);
        byteBuffer.put(salt);
        byteBuffer.put(iv);
        byteBuffer.put(encryptedData);
        
        // Return as Base64 encoded string
        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }
    
    /**
     * Decrypt data that was encrypted with the encrypt method.
     * 
     * @param encryptedData The Base64 encoded encrypted data
     * @param password The password used for encryption
     * @return The decrypted data
     * @throws Exception If decryption fails
     */
    public String decrypt(String encryptedData, String password) throws Exception {
        // Decode the Base64 string
        byte[] decoded = Base64.getDecoder().decode(encryptedData);
        ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
        
        // Extract salt
        byte[] salt = new byte[SALT_LENGTH_BYTE];
        byteBuffer.get(salt);
        
        // Extract IV
        byte[] iv = new byte[IV_LENGTH_BYTE];
        byteBuffer.get(iv);
        
        // Extract encrypted data
        byte[] ciphertext = new byte[byteBuffer.remaining()];
        byteBuffer.get(ciphertext);
        
        // Derive key from password and salt
        SecretKey key = getAESKeyFromPassword(password, salt);
        
        // Initialize cipher for decryption
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
        
        // Decrypt the data
        byte[] decryptedData = cipher.doFinal(ciphertext);
        
        // Return as string
        return new String(decryptedData, StandardCharsets.UTF_8);
    }
    
    /**
     * Save encrypted credentials to the key store.
     * 
     * @param key The key to save under
     * @param data The data to encrypt and save
     * @param password The password to use for encryption
     * @throws Exception If saving fails
     */
    public void saveCredential(String key, String data, String password) throws Exception {
        String encrypted = encrypt(data, password);
        Path path = Paths.get(keyStorePath, key + ".enc");
        Files.write(path, encrypted.getBytes(StandardCharsets.UTF_8));
        LOGGER.info("Saved encrypted credential for: " + key);
    }
    
    /**
     * Load and decrypt credentials from the key store.
     * 
     * @param key The key to load
     * @param password The password to use for decryption
     * @return The decrypted data
     * @throws Exception If loading fails
     */
    public String loadCredential(String key, String password) throws Exception {
        Path path = Paths.get(keyStorePath, key + ".enc");
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Credential not found: " + key);
        }
        
        String encrypted = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return decrypt(encrypted, password);
    }
    
    /**
     * Generate a strong password.
     * 
     * @param length The length of the password
     * @return A randomly generated password
     */
    public String generateStrongPassword(int length) {
        // Characters to use in the password
        String upperChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerChars = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String specialChars = "!@#$%^&*()_-+=<>?";
        String allChars = upperChars + lowerChars + digits + specialChars;
        
        // Generate random password
        StringBuilder password = new StringBuilder();
        
        // Ensure password contains at least one of each type
        password.append(upperChars.charAt(secureRandom.nextInt(upperChars.length())));
        password.append(lowerChars.charAt(secureRandom.nextInt(lowerChars.length())));
        password.append(digits.charAt(secureRandom.nextInt(digits.length())));
        password.append(specialChars.charAt(secureRandom.nextInt(specialChars.length())));
        
        // Fill the rest of the password
        for (int i = 4; i < length; i++) {
            password.append(allChars.charAt(secureRandom.nextInt(allChars.length())));
        }
        
        // Shuffle the password
        char[] chars = password.toString().toCharArray();
        for (int i = 0; i < chars.length; i++) {
            int j = secureRandom.nextInt(chars.length);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        
        return new String(chars);
    }
    
    /**
     * Create a key from a password and salt using PBKDF2.
     */
    private SecretKey getAESKeyFromPassword(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, AES_KEY_BIT);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
