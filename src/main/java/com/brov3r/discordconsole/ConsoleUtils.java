package com.brov3r.discordconsole;

import org.tinylog.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                            // Truncate if message exceeds MAX_MESSAGE_LENGTH
                            if (message.length() > MAX_MESSAGE_LENGTH) {
                                message = message.substring(0, MAX_MESSAGE_LENGTH);
                            }

                            // Flush accumulated messages if adding this message would exceed MAX_MESSAGE_LENGTH
                            if (accumulatedMessage.length() + message.length() > MAX_MESSAGE_LENGTH) {
                                allMessages.add(accumulatedMessage.toString());
                                accumulatedMessage.setLength(0);
                            }

                            accumulatedMessage.append(message).append("\n");
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
            String text = formatMessage(message);

            if (text == null) return;

            messageQueue.put(text);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Formatting a message
     * @param msg log message
     */
    private static String formatMessage(String msg) {
        StringBuilder sb = new StringBuilder();

        String logLevel = "INFO";
        String outputMessage = msg;

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        String formattedDateTime = now.format(formatter);
        sb.append("[").append(formattedDateTime).append("] ");

        Matcher matcherToUse = getMatcher(msg);

        if (matcherToUse != null) {
            String firstWord = matcherToUse.group(1);
            String message = matcherToUse.group(2);

            outputMessage = getFormatedMessage(message);

            if (outputMessage.isEmpty()) return null;

            switch (firstWord) {
                case "DEBUG":
                    logLevel = "DEBUG";
                    break;
                case "WARN":
                    logLevel = "WARN";
                    break;
                case "ERROR":
                    logLevel = "ERROR";
                    break;
                case "TRACE":
                    logLevel = "TRACE";
                    break;
                default:
                    if (outputMessage.startsWith("DEBUG:")) {
                        outputMessage = outputMessage.substring(6);
                        logLevel = "DEBUG";
                    } else if (outputMessage.startsWith("WARN:")) {
                        outputMessage = outputMessage.substring(5);
                        logLevel = "WARN";
                    } else if (outputMessage.startsWith("ERROR:")) {
                        outputMessage = outputMessage.substring(6);
                        logLevel = "ERROR";
                    } else if (outputMessage.startsWith("TRACE:")) {
                        outputMessage = outputMessage.substring(6);
                        logLevel = "TRACE";
                    } else {
                        logLevel = "INFO";
                    }
                    break;
            }
        }

        Pattern specialPattern = Pattern.compile("^\\[(.)\\]\\s*(.*)$");
        Matcher specialMatcher = specialPattern.matcher(outputMessage);

        if (specialMatcher.find()) {
            char specialChar = specialMatcher.group(1).charAt(0);
            outputMessage = specialMatcher.group(2).trim();
            logLevel = switch (specialChar) {
                case '!' -> "ERROR";
                case '?' -> "WARN";
                default -> "INFO";
            };
        }

        if (outputMessage.length() == msg.length()) {
            outputMessage = getFormatedMessage(msg);
        }

        sb.append(logLevel).append(" > ").append(getFormatedMessage(outputMessage));
        return sb.toString();
    }

    /**
     * Formats the input message by applying several transformations:
     * 1. Removes text enclosed in square brackets followed by '>'.
     * 2. Trims leading digits and whitespace.
     * 3. Replaces multiple occurrences of '>' with a single occurrence surrounded by spaces.
     * 4. Capitalizes the first letter of the resulting message.
     *
     * @param message The input message to be formatted.
     * @return The formatted message.
     */
    private static String getFormatedMessage(String message) {
        message = message.replaceAll("\\[.*?\\]\\s*>\\s*", "").trim();
        message = message.replaceAll("^\\d*\\s", "").trim();
        message = message.replaceAll("\\s*>\\s*", " > ").trim();
        message = capitalizeFirstLetter(message);
        message = message.replace("*", "");
        return message;
    }

    /**
     * Returns a matcher for the input text based on two regular expression patterns:
     * 1. Captures the first word and the message after two occurrences of '>'.
     * 2. Captures the first word and the message after a single occurrence of '>'.
     * If the first pattern matches, its matcher is returned. Otherwise, if the second pattern matches, its matcher is returned.
     *
     * @param text The input text to be matched.
     * @return The matcher that matches the input text, or {@code null} if neither pattern matches.
     */
    private static Matcher getMatcher(String text) {
        // Regular expression to capture the first word and message after '>'
        Pattern pattern1 = Pattern.compile("^(\\w+).*?>.*?>\\s(.*)$");
        Matcher matcher1 = pattern1.matcher(text);

        // Regular expression to capture the first word and message after '>'
        Pattern pattern2 = Pattern.compile("^(\\w+).*?>\\s(.*)$");
        Matcher matcher2 = pattern2.matcher(text);

        Matcher matcherToUse = null;
        if (matcher1.find()) {
            matcherToUse = matcher1;
        } else if (matcher2.find()) {
            matcherToUse = matcher2;
        }
        return matcherToUse;
    }

    /**
     * Capitalizes the first letter of a string.
     *
     * @param message The input string.
     * @return The input string with the first letter capitalized.
     */
    private static String capitalizeFirstLetter(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        return message.substring(0, 1).toUpperCase() + message.substring(1);
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