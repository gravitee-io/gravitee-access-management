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
package io.gravitee.am.management.service.impl;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.management.service.AgentCardService;
import io.gravitee.am.service.exception.AgentCardFetchException;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * @author GraviteeSource Team
 */
@Component
public class AgentCardServiceImpl implements AgentCardService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentCardServiceImpl.class);

    private static final int MAX_BODY_SIZE = 512 * 1024;
    private static final long TIMEOUT_MS = 5_000L;

    static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
            "^(10\\.\\d+\\.\\d+\\.\\d+|" +
            "172\\.(1[6-9]|2\\d|3[01])\\.\\d+\\.\\d+|" +
            "192\\.168\\.\\d+\\.\\d+|" +
            "169\\.254\\.\\d+\\.\\d+)$"
    );

    @Lazy
    @Autowired
    @Qualifier("agentCardWebClient")
    private WebClient client;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Single<String> fetchAgentCard(String agentCardUrl) {
        try {
            URI uri = URI.create(agentCardUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return Single.error(new IllegalArgumentException("Only http/https schemes are allowed for agentCardUrl"));
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return Single.error(new IllegalArgumentException("Invalid agentCardUrl: missing host"));
            }
            if (UriBuilder.isLocalhost(host)) {
                return Single.error(new IllegalArgumentException("SSRF protection: localhost target is not allowed"));
            }
            if (PRIVATE_IP_PATTERN.matcher(host).matches()) {
                return Single.error(new IllegalArgumentException("SSRF protection: private IP target is not allowed"));
            }
        } catch (IllegalArgumentException | NullPointerException | UnsupportedOperationException e) {
            return Single.error(new IllegalArgumentException("Invalid agentCardUrl: " + e.getMessage()));
        }

        return client.getAbs(agentCardUrl)
                .timeout(TIMEOUT_MS)
                .rxSend()
                .flatMap(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.warn("Agent card fetch returned non-200 status: {} for URL: {}", response.statusCode(), agentCardUrl);
                        return Single.error(new AgentCardFetchException(agentCardUrl, "URL returned status " + response.statusCode()));
                    }
                    String body = response.bodyAsString();
                    if (body == null) {
                        return Single.error(new AgentCardFetchException(agentCardUrl, "response body is empty"));
                    }
                    if (body.length() > MAX_BODY_SIZE) {
                        return Single.error(new AgentCardFetchException(agentCardUrl, "response exceeds maximum allowed size"));
                    }
                    try {
                        objectMapper.readTree(body);
                    } catch (JacksonException e) {
                        LOGGER.warn("Agent card response is not valid JSON for URL: {}", agentCardUrl);
                        return Single.error(new AgentCardFetchException(agentCardUrl, "response is not valid JSON"));
                    }
                    return Single.just(body);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AgentCardFetchException || ex instanceof IllegalArgumentException) {
                        return Single.error(ex);
                    }
                    LOGGER.warn("Failed to fetch agent card from URL: {}", agentCardUrl, ex);
                    return Single.error(new AgentCardFetchException(agentCardUrl, "URL may be unreachable or returned invalid data"));
                });
    }
}
