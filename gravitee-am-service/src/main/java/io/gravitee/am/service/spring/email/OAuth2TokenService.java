/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.service.spring.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for managing OAuth2 access tokens for email authentication.
 *
 * This service automatically exchanges refresh tokens for access tokens
 * via the configured OAuth2 token endpoint. It caches access tokens
 * and refreshes them before expiration.
 *
 * Thread-safe for concurrent email sends.
 *
 * @author GraviteeSource Team
 */
@Service
public class OAuth2TokenService {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2TokenService.class);
    private static final int TOKEN_EXPIRY_BUFFER_SECONDS = 300; // Refresh 5 minutes before expiry

    private final ObjectMapper objectMapper;
    private final Vertx vertx;
    private final WebClient webClient;
    private final Lock lock = new ReentrantLock();

    // Configuration
    private String tokenEndpoint;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String scope;

    // Cached token state
    private String cachedAccessToken;
    private Instant tokenExpiryTime;

    public OAuth2TokenService(Vertx vertx) {
        this.vertx = vertx;
        this.webClient = WebClient.create(vertx);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Initialize the token service with OAuth2 configuration.
     */
    public void initialize(String tokenEndpoint, String clientId, String clientSecret,
                          String refreshToken, String scope) {
        this.tokenEndpoint = tokenEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.scope = scope;

        logger.info("OAuth2TokenService initialized for endpoint: {}", tokenEndpoint);
    }

    /**
     * Get a valid access token. If the cached token is expired or missing,
     * this will automatically refresh it.
     *
     * @return Valid access token
     * @throws RuntimeException if token refresh fails
     */
    public String getAccessToken() {
        lock.lock();
        try {
            if (isTokenExpired()) {
                logger.debug("Access token expired or missing, refreshing...");
                refreshAccessToken();
            }
            return cachedAccessToken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if the cached access token is expired or missing.
     */
    private boolean isTokenExpired() {
        if (cachedAccessToken == null || tokenExpiryTime == null) {
            return true;
        }
        // Refresh if we're within the buffer time of expiry
        return Instant.now().plusSeconds(TOKEN_EXPIRY_BUFFER_SECONDS).isAfter(tokenExpiryTime);
    }

    /**
     * Refresh the access token by calling the OAuth2 token endpoint.
     */
    private void refreshAccessToken() {
        try {
            logger.debug("Calling token endpoint to refresh access token");

            URI uri = new URI(tokenEndpoint);
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : (uri.getScheme().equals("https") ? 443 : 80);
            String path = uri.getPath();
            boolean ssl = uri.getScheme().equals("https");

            // Build form data
            String formData = String.format(
                "client_id=%s&client_secret=%s&refresh_token=%s&grant_type=refresh_token&scope=%s",
                urlEncode(clientId),
                urlEncode(clientSecret),
                urlEncode(refreshToken),
                urlEncode(scope)
            );

            CompletableFuture<OAuth2TokenResponse> future = new CompletableFuture<>();

            var request = webClient.post(port, host, path)
                .ssl(ssl)
                .putHeader("Content-Type", "application/x-www-form-urlencoded")
                .sendBuffer(Buffer.buffer(formData));

            request.onSuccess(response -> {
                try {
                    if (response.statusCode() == 200) {
                        OAuth2TokenResponse tokenResponse = objectMapper.readValue(
                            response.bodyAsString(),
                            OAuth2TokenResponse.class
                        );
                        future.complete(tokenResponse);
                    } else {
                        future.completeExceptionally(new RuntimeException(
                            "Token refresh failed with status " + response.statusCode() +
                            ": " + response.bodyAsString()
                        ));
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });

            request.onFailure(future::completeExceptionally);

            // Wait for response (synchronous for simplicity)
            OAuth2TokenResponse tokenResponse = future.get();

            // Update cached token
            cachedAccessToken = tokenResponse.getAccessToken();

            // Update refresh token if provider returned a new one
            if (tokenResponse.getRefreshToken() != null) {
                refreshToken = tokenResponse.getRefreshToken();
                logger.debug("Refresh token updated by provider");
            }

            // Calculate expiry time
            if (tokenResponse.getExpiresIn() != null) {
                tokenExpiryTime = Instant.now().plusSeconds(tokenResponse.getExpiresIn());
                logger.debug("Access token refreshed, expires at: {}", tokenExpiryTime);
            } else {
                // Default to 1 hour if not provided
                tokenExpiryTime = Instant.now().plusSeconds(3600);
                logger.debug("Access token refreshed, using default 1 hour expiry");
            }

        } catch (Exception e) {
            logger.error("Failed to refresh access token", e);
            throw new RuntimeException("Failed to refresh OAuth2 access token", e);
        }
    }

    /**
     * Simple URL encoding for form data.
     */
    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}
