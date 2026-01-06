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
import io.gravitee.am.service.http.WebClientBuilder;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static io.gravitee.am.common.web.UriBuilder.encodeURIComponent;

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
    private static final int TOKEN_EXPIRY_BUFFER_SECONDS = 30; // Refresh 30 seconds before expiry

    private final ObjectMapper objectMapper;
    private final WebClientBuilder webClientBuilder;
    private final Vertx vertx;
    private final Lock lock = new ReentrantLock();

    private String tokenEndpoint;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String scope;

    private String cachedAccessToken;
    private Instant tokenExpiryTime;
    private WebClient webClient;

    @Autowired
    public OAuth2TokenService(WebClientBuilder webClientBuilder, Vertx vertx) {
        this.webClientBuilder = webClientBuilder;
        this.vertx = vertx;
        this.objectMapper = new ObjectMapper();
    }

    public void initialize(@NotBlank String tokenEndpoint, @NotBlank String clientId, @NotBlank String clientSecret,
                           @NotBlank String refreshToken, @NotBlank String scope) {
        this.tokenEndpoint = tokenEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.scope = scope;

        try {
            this.webClient = webClientBuilder.createWebClient(vertx, URI.create(tokenEndpoint).toURL());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create WebClient for OAuth2 token endpoint: " + tokenEndpoint, e);
        }
    }

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

    private boolean isTokenExpired() {
        if (cachedAccessToken == null || tokenExpiryTime == null) {
            return true;
        }
        return Instant.now().plusSeconds(TOKEN_EXPIRY_BUFFER_SECONDS).isAfter(tokenExpiryTime);
    }

    private void refreshAccessToken() {
        try {
            logger.debug("Calling token endpoint to refresh access token");

            URI uri = new URI(tokenEndpoint);
            String path = uri.getPath();

            String formData = String.format(
                    "client_id=%s&client_secret=%s&refresh_token=%s&grant_type=refresh_token&scope=%s",
                    encodeURIComponent(clientId),
                    encodeURIComponent(clientSecret),
                    encodeURIComponent(refreshToken),
                    encodeURIComponent(scope)
            );

            HttpResponse<Buffer> response = webClient.post(path)
                    .putHeader("Content-Type", "application/x-www-form-urlencoded")
                    .rxSendBuffer(Buffer.buffer(formData))
                    .blockingGet();

            if (response.statusCode() != 200) {
                throw new RuntimeException(String.format("Token refresh failed with status %s:%s ", response.statusCode(), response.bodyAsString()));
            }

            OAuth2TokenResponse tokenResponse = objectMapper.readValue(
                    response.bodyAsString(),
                    OAuth2TokenResponse.class
            );

            cachedAccessToken = tokenResponse.getAccessToken();

            if (tokenResponse.getRefreshToken() != null) {
                refreshToken = tokenResponse.getRefreshToken();
                logger.debug("Refresh token updated by provider");
            }

            if (tokenResponse.getExpiresIn() != null) {
                tokenExpiryTime = Instant.now().plusSeconds(tokenResponse.getExpiresIn());
                logger.debug("Access token refreshed, expires at: {}", tokenExpiryTime);
            } else {
                tokenExpiryTime = null;
            }

        } catch (Exception e) {
            logger.error("Failed to refresh access token", e);
            throw new RuntimeException("Failed to refresh OAuth2 access token", e);
        }
    }
}
