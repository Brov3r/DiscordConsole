package com.brov3r.discordconsole;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Utility class for managing asynchronous logging to Discord via webhooks.
 */
public class ConsoleUtils {
    private static final ConsoleMessage consoleMessage = new ConsoleMessage();
    private static final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final int RATE_LIMIT_INTERVAL = 2000;
    private static final int MAX_MESSAGE_LENGTH = 2000;

    static {
        executor.submit(() -> {
            while (true) {
                try {
                    List<String> allMessages = new ArrayList<>();
                    StringBuilder accumulatedMessage = new StringBuilder();
                    long startTime = System.currentTimeMillis();

                    // Collect messages for RATE_LIMIT_INTERVAL milliseconds
                    while (System.currentTimeMillis() - startTime < RATE_LIMIT_INTERVAL) {
                        String message = messageQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (message != null) {
                            LocalDateTime now = LocalDateTime.now();
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
                            String formattedDateTime = now.format(formatter);
                            String finalMessage = "[" + formattedDateTime + "] " + message;

                            // Truncate if message exceeds MAX_MESSAGE_LENGTH
                            if (finalMessage.length() > MAX_MESSAGE_LENGTH) {
                                finalMessage = finalMessage.substring(0, MAX_MESSAGE_LENGTH);
                            }

                            // Flush accumulated messages if adding this message would exceed MAX_MESSAGE_LENGTH
                            if (accumulatedMessage.length() + finalMessage.length() > MAX_MESSAGE_LENGTH) {
                                allMessages.add(accumulatedMessage.toString());
                                accumulatedMessage.setLength(0);
                            }

                            accumulatedMessage.append(finalMessage).append("\n");
                        }
                    }

                    // Add any remaining accumulated messages
                    if (!accumulatedMessage.isEmpty()) {
                        allMessages.add(accumulatedMessage.toString());
                    }

                    // Send all collected messages to Discord
                    for (String msg : allMessages) {
                        consoleMessage.setContent(msg);

                        // Send message if ConsoleMessage is valid
                        if (consoleMessage.isValid()) {
                            consoleMessage.execute().join();
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.out.println("[!] An error occurred while processing the DiscordLog message: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Sends a log message to be asynchronously logged to Discord.
     *
     * @param message The log message to send
     */
    public static void sendLog(String message) {
        try {
            messageQueue.put(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Initiates shutdown of the ConsoleUtils, stopping the message processing thread.
     * This method blocks until all pending messages are sent or the timeout expires.
     */
    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}