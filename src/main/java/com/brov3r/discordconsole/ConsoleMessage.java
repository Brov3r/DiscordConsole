package com.brov3r.discordconsole;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import org.json.JSONObject;

/**
 * Class used to execute Discord Webhooks with automatic retry on rate limits.
 */
public class ConsoleMessage {
    private final String url;
    private String content;
    private final String username;
    private final String avatarUrl;
    public HttpClient client;

    /**
     * Constructs a new ConsoleMessage instance.
     *
     * @throws IllegalArgumentException if the URL is null or invalid
     */
    public ConsoleMessage() {
        this.url = Main.getInstance().getDefaultConfig().getString("webhookURL");
        this.client = HttpClient.newHttpClient();
        this.username = Main.getInstance().getDefaultConfig().getString("consoleUsername");
        this.avatarUrl = Main.getInstance().getDefaultConfig().getString("consoleAvatarURL");
    }

    /**
     * Sets the content of the message to be sent.
     *
     * @param content The content of the message
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Checks if the ConsoleMessage instance is valid for sending.
     *
     * @return true if the instance is valid, false otherwise
     */
    public boolean isValid() {
        return this.url != null && this.url.startsWith("https://") && this.username != null && this.avatarUrl != null;
    }

    /**
     * Executes the webhook by sending a POST request to the Discord webhook URL.
     *
     * @return A CompletableFuture that completes when the request is done
     * @throws IllegalStateException if the content is null
     */
    public CompletableFuture<Void> execute() {
        JSONObject json = new JSONObject();
        json.put("content", this.content);
        json.put("username", this.username);
        json.put("avatar_url", this.avatarUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.url))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Java-ConsoleMessage")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 429) {
                        return null;
                    } else if (response.statusCode() != 200 && response.statusCode() != 204) {
                        System.out.println("[!]Failed to send webhook: " + response.body());
                        return null;
                    }
                    return null;
                });
    }
}