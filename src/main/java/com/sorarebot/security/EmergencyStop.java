package com.sorarebot.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Emergency stop mechanism that can halt all trading activities.
 * Can be triggered programmatically or via a file-based deadman's switch.
 */
public class EmergencyStop {
    private static final Logger LOGGER = Logger.getLogger(EmergencyStop.class.getName());
    
    private final AtomicBoolean emergencyStopActive = new AtomicBoolean(false);
    private final Path emergencyStopFile;
    private final Path emergencyReasonFile;
    private final long fileCheckIntervalMs;
    private Thread fileWatcherThread;
    private boolean fileWatcherRunning = false;
    
    /**
     * Create a new emergency stop mechanism.
     * 
     * @param emergencyStopDir Directory to store the emergency stop files
     * @param fileCheckIntervalMs How often to check for the stop file (in milliseconds)
     */
    public EmergencyStop(String emergencyStopDir, long fileCheckIntervalMs) {
        this.emergencyStopFile = Paths.get(emergencyStopDir, "EMERGENCY_STOP");
        this.emergencyReasonFile = Paths.get(emergencyStopDir, "EMERGENCY_REASON.txt");
        this.fileCheckIntervalMs = fileCheckIntervalMs;
        
        // Check if the emergency stop is already active from a previous run
        if (Files.exists(emergencyStopFile)) {
            try {
                String reason = Files.exists(emergencyReasonFile) 
                    ? new String(Files.readAllBytes(emergencyReasonFile))
                    : "Unknown (stop file exists from previous run)";
                
                emergencyStopActive.set(true);
                LOGGER.severe("EMERGENCY STOP IS ACTIVE: " + reason);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error checking emergency stop files", e);
            }
        }
        
        // Create the directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(emergencyStopDir));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error creating emergency stop directory", e);
        }
    }
    
    /**
     * Start monitoring for the emergency stop file.
     */
    public void startFileWatcher() {
        if (fileWatcherRunning) {
            return;
        }
        
        fileWatcherRunning = true;
        fileWatcherThread = new Thread(() -> {
            while (fileWatcherRunning) {
                try {
                    if (Files.exists(emergencyStopFile) && !emergencyStopActive.get()) {
                        String reason = Files.exists(emergencyReasonFile) 
                            ? new String(Files.readAllBytes(emergencyReasonFile))
                            : "Unknown (external file creation)";
                        
                        triggerEmergencyStop(reason);
                    }
                    
                    Thread.sleep(fileCheckIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error in emergency stop file watcher", e);
                }
            }
        });
        
        fileWatcherThread.setName("EmergencyStopWatcher");
        fileWatcherThread.setDaemon(true);
        fileWatcherThread.start();
        
        LOGGER.info("Emergency stop file watcher started");
    }
    
    /**
     * Stop the file watcher thread.
     */
    public void stopFileWatcher() {
        fileWatcherRunning = false;
        if (fileWatcherThread != null) {
            fileWatcherThread.interrupt();
        }
    }
    
    /**
     * Trigger an emergency stop.
     * 
     * @param reason The reason for the emergency stop
     */
    public void triggerEmergencyStop(String reason) {
        if (emergencyStopActive.compareAndSet(false, true)) {
            LOGGER.severe("!!! EMERGENCY STOP TRIGGERED !!! Reason: " + reason);
            
            try {
                // Create the stop file
                Files.write(emergencyStopFile, 
                           ("EMERGENCY STOP TRIGGERED AT " + Instant.now()).getBytes(),
                           StandardOpenOption.CREATE);
                
                // Write the reason to the reason file
                Files.write(emergencyReasonFile, 
                           reason.getBytes(),
                           StandardOpenOption.CREATE);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error creating emergency stop files", e);
            }
        }
    }
    
    /**
     * Check if the emergency stop is active.
     * 
     * @return true if the emergency stop is active, false otherwise
     */
    public boolean isEmergencyStopActive() {
        return emergencyStopActive.get();
    }
    
    /**
     * Clear the emergency stop condition.
     * This should only be called after the issue has been investigated and resolved.
     * 
     * @param override Set to true to force clear even if files exist
     * @return true if successfully cleared, false otherwise
     */
    public boolean clearEmergencyStop(boolean override) {
        if (!emergencyStopActive.get()) {
            return true; // Already cleared
        }
        
        // Check if the files still exist
        boolean filesExist = Files.exists(emergencyStopFile) || Files.exists(emergencyReasonFile);
        
        if (filesExist && !override) {
            LOGGER.warning("Cannot clear emergency stop - files still exist. Use override=true to force clear.");
            return false;
        }
        
        // Delete the files if they exist
        try {
            if (Files.exists(emergencyStopFile)) {
                Files.delete(emergencyStopFile);
            }
            
            if (Files.exists(emergencyReasonFile)) {
                Files.delete(emergencyReasonFile);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error deleting emergency stop files", e);
            return false;
        }
        
        emergencyStopActive.set(false);
        LOGGER.info("Emergency stop cleared");
        return true;
    }
    
    /**
     * Get the reason for the emergency stop.
     * 
     * @return the reason for the emergency stop, or null if not active
     */
    public String getEmergencyStopReason() {
        if (!emergencyStopActive.get()) {
            return null;
        }
        
        try {
            if (Files.exists(emergencyReasonFile)) {
                return new String(Files.readAllBytes(emergencyReasonFile));
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading emergency reason file", e);
        }
        
        return "Unknown (reason file not available)";
    }
}