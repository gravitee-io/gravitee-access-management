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
package io.gravitee.am.gateway.handler.oidc.service.cimd.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.oidc.service.cimd.CIMDException;
import io.gravitee.am.gateway.handler.oidc.service.cimd.CIMDMetadataCache;
import io.gravitee.am.gateway.handler.oidc.service.cimd.CIMDMetadataDocument;
import io.gravitee.am.gateway.handler.oidc.service.cimd.CIMDMetadataFetcher;
import io.gravitee.am.gateway.handler.oidc.service.cimd.SSRFValidator;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.CIMDAuditBuilder;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.net.URI;
import java.util.Optional;

/**
 * Fetches CIMD metadata documents via HTTP(S) with SSRF protection and caching.
 *
 * @author GraviteeSource Team
 */
public class CIMDMetadataFetcherImpl implements CIMDMetadataFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CIMDMetadataFetcherImpl.class);

    @Autowired
    @Qualifier("oidcWebClient")
    private WebClient webClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Domain domain;

    @Autowired
    private AuditService auditService;

    private final SSRFValidator ssrfValidator = new SSRFValidator();

    private CIMDMetadataCache cache;

    @PostConstruct
    public void init() {
        CIMDSettings settings = domain.getOidc() != null ? domain.getOidc().getCimdSettings() : null;
        initCache(settings);
        if (cache != null) {
            LOGGER.info("CIMD metadata cache initialized for domain {} (maxEntries={}, ttl={}s)",
                    domain.getId(), settings.getCacheMaxEntries(), settings.getCacheTtlSeconds());
        }
    }

    @Override
    public Single<CIMDMetadataDocument> fetch(String clientIdUri, String domainId, CIMDSettings settings) {
        // Check cache first
        if (cache != null) {
            Optional<CIMDMetadataDocument> cached = cache.get(domainId, clientIdUri);
            if (cached.isPresent()) {
                LOGGER.debug("CIMD cache hit for domain={}, clientId={}", domainId, clientIdUri);
                reportFetched(clientIdUri, domainId, cached.get().getSoftwareId(), 0, true);
                return Single.just(cached.get());
            }
        }

        URI uri;
        try {
            uri = URI.create(clientIdUri);
        } catch (IllegalArgumentException e) {
            return Single.error(new CIMDException("Invalid CIMD metadata URI: " + clientIdUri, e));
        }

        // SSRF validation
        try {
            ssrfValidator.validate(uri, settings);
        } catch (CIMDException e) {
            reportRejected(clientIdUri, domainId, e.getMessage(), uri.getHost());
            return Single.error(e);
        }

        int maxSizeBytes = settings.getMaxResponseSizeKb() * 1024;
        long fetchStart = System.currentTimeMillis();

        return webClient.getAbs(clientIdUri)
                .timeout(settings.getFetchTimeoutMs())
                .rxSend()
                .flatMap(response -> {
                    if (response.statusCode() != 200) {
                        return Single.error(new CIMDException(
                                "CIMD metadata fetch failed with status " + response.statusCode() + " for URI: " + clientIdUri));
                    }

                    // Pre-check Content-Length header before reading body
                    String contentLengthHeader = response.getHeader("Content-Length");
                    if (contentLengthHeader != null) {
                        try {
                            if (Long.parseLong(contentLengthHeader) > maxSizeBytes) {
                                return Single.error(new CIMDException(
                                        "CIMD metadata response Content-Length exceeds maximum size of "
                                                + settings.getMaxResponseSizeKb() + "KB for URI: " + clientIdUri));
                            }
                        } catch (NumberFormatException ignored) {
                            // Content-Length header is malformed; fall through to body size check
                        }
                    }

                    io.vertx.core.buffer.Buffer bodyBuffer = response.body();
                    if (bodyBuffer == null || bodyBuffer.length() == 0) {
                        return Single.error(new CIMDException("CIMD metadata response is empty for URI: " + clientIdUri));
                    }

                    if (bodyBuffer.length() > maxSizeBytes) {
                        return Single.error(new CIMDException(
                                "CIMD metadata response exceeds maximum size of " + settings.getMaxResponseSizeKb() + "KB for URI: " + clientIdUri));
                    }

                    String body = bodyBuffer.toString(java.nio.charset.StandardCharsets.UTF_8);

                    try {
                        CIMDMetadataDocument document = objectMapper.readValue(body, CIMDMetadataDocument.class);

                        // Cache the result
                        if (cache != null) {
                            cache.put(domainId, clientIdUri, document);
                        }

                        reportFetched(clientIdUri, domainId, document.getSoftwareId(),
                                System.currentTimeMillis() - fetchStart, false);

                        return Single.just(document);
                    } catch (Exception e) {
                        return Single.error(new CIMDException("Failed to parse CIMD metadata document from URI: " + clientIdUri, e));
                    }
                })
                .onErrorResumeNext(err -> {
                    if (err instanceof CIMDException) {
                        return Single.error(err);
                    }
                    return Single.error(new CIMDException("CIMD metadata fetch failed for URI: " + clientIdUri, err));
                });
    }

    public void initCache(CIMDSettings settings) {
        if (settings != null && settings.isEnabled()) {
            this.cache = new CIMDMetadataCache(settings.getCacheMaxEntries(), settings.getCacheTtlSeconds());
        }
    }

    // Visible for testing
    void setWebClient(WebClient webClient) {
        this.webClient = webClient;
    }

    void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void setCache(CIMDMetadataCache cache) {
        this.cache = cache;
    }

    private void reportFetched(String metadataUri, String domainId, String softwareId,
                               long fetchDurationMs, boolean cacheHit) {
        try {
            auditService.report(AuditBuilder.builder(CIMDAuditBuilder.class)
                    .reference(Reference.domain(domainId))
                    .metadataUri(metadataUri)
                    .softwareId(softwareId)
                    .fetchDurationMs(fetchDurationMs)
                    .cacheHit(cacheHit));
        } catch (Exception e) {
            LOGGER.warn("Failed to report CIMD fetch audit", e);
        }
    }

    private void reportRejected(String metadataUri, String domainId, String reason, String host) {
        try {
            auditService.report(AuditBuilder.builder(CIMDAuditBuilder.class)
                    .rejected()
                    .reference(Reference.domain(domainId))
                    .metadataUri(metadataUri)
                    .rejectionReason(reason)
                    .resolvedIp(host));
        } catch (Exception e) {
            LOGGER.warn("Failed to report CIMD rejection audit", e);
        }
    }
}
