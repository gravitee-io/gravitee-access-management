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
package io.gravitee.am.gateway.handler.common.client.cimd;

import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.service.utils.RetryAtMostWithDelay;
import io.gravitee.am.service.utils.vertx.BoundedBufferWriteStream;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.core.streams.WriteStream;
import io.vertx.rxjava3.ext.web.codec.BodyCodec;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * Fetches CIMD client logos with the same trust and size bounds as metadata pre-fetch, and stores
 * them in {@link CimdMetadataDocumentManager}.
 */
@Slf4j
public class CimdLogoCacheService {

    private static final int FETCH_RETRY_ATTEMPTS = 3;
    private static final int FETCH_RETRY_DELAY_MS = 100;
    static final long MAX_LOGO_SIZE_BYTES = 256L * 1024L;

    private final WebClient webClient;
    private final CimdMetadataDocumentManager cimdMetadataDocumentManager;
    private final CimdUriTrustValidator cimdUriTrustValidator;

    public CimdLogoCacheService(
            WebClient webClient,
            CimdMetadataDocumentManager cimdMetadataDocumentManager,
            CimdUriTrustValidator cimdUriTrustValidator) {
        this.webClient = webClient;
        this.cimdMetadataDocumentManager = cimdMetadataDocumentManager;
        this.cimdUriTrustValidator = cimdUriTrustValidator;
    }

    /**
     * Fire-and-forget logo download when not already cached.
     */
    public void prefetchLogoAsync(String clientId, String logoUri, long metadataTtlSeconds, CIMDSettings settings) {
        if (logoUri == null || logoUri.isBlank()) {
            return;
        }
        if (cimdMetadataDocumentManager.getLogoByClientId(clientId).isPresent()) {
            return;
        }

        final URI uri;
        try {
            uri = cimdUriTrustValidator.parseHttpUrl(logoUri, "logo_uri");
        } catch (Exception ex) {
            log.debug("CIMD logo pre-fetch skipped — invalid URI: {}", logoUri);
            return;
        }
        try {
            cimdUriTrustValidator.validateTrust(uri, settings, "logo_uri");
        } catch (Exception ex) {
            log.debug("CIMD logo pre-fetch skipped — URI not trusted: {}", logoUri);
            return;
        }

        cimdUriTrustValidator.validateResolvableHost(uri.getHost(), "logo_uri", settings).subscribe(
                () -> fetchLogoSingle(logoUri, metadataTtlSeconds, settings)
                        .subscribe(
                                opt -> opt.ifPresent(logo -> {
                                    cimdMetadataDocumentManager.putLogo(clientId, logo);
                                    log.debug("CIMD logo cached for clientId {}", clientId);
                                }),
                                err -> log.debug(
                                        "CIMD logo pre-fetch failed for uri {}: {}",
                                        logoUri,
                                        err.getMessage())),
                err -> log.debug("CIMD logo pre-fetch skipped — {}", err.getMessage()));
    }

    /**
     * Synchronously (reactively) fetch and cache the logo; returns empty when the URI is invalid,
     * untrusted, fetch fails, or the response is not a usable image.
     */
    public Single<Optional<CachedLogo>> fetchAndCacheNow(
            String clientId, String logoUri, long metadataTtlSeconds, CIMDSettings settings) {
        if (logoUri == null || logoUri.isBlank()) {
            return Single.just(Optional.empty());
        }
        Optional<CachedLogo> existing = cimdMetadataDocumentManager.getLogoByClientId(clientId);
        if (existing.isPresent()) {
            return Single.just(existing);
        }

        final URI uri;
        try {
            uri = cimdUriTrustValidator.parseHttpUrl(logoUri, "logo_uri");
        } catch (Exception ex) {
            log.debug("CIMD logo fetch skipped — invalid URI: {}", logoUri);
            return Single.just(Optional.empty());
        }
        try {
            cimdUriTrustValidator.validateTrust(uri, settings, "logo_uri");
        } catch (Exception ex) {
            log.debug("CIMD logo fetch skipped — URI not trusted: {}", logoUri);
            return Single.just(Optional.empty());
        }

        return cimdUriTrustValidator.validateResolvableHost(uri.getHost(), "logo_uri", settings)
                .andThen(fetchLogoSingle(logoUri, metadataTtlSeconds, settings))
                .flatMap(opt -> {
                    opt.ifPresent(logo -> cimdMetadataDocumentManager.putLogo(clientId, logo));
                    return Single.just(opt);
                })
                .onErrorResumeNext(e -> Single.just(Optional.empty()));
    }

    private Single<Optional<CachedLogo>> fetchLogoSingle(
            String logoUri, long metadataTtlSeconds, CIMDSettings settings) {
        final long timeoutMs = resolveTimeoutMs(settings);

        return Single.defer(() -> {
                    final var logoCollector = new BoundedBufferWriteStream(MAX_LOGO_SIZE_BYTES);
                    return webClient.getAbs(logoUri)
                        .timeout(timeoutMs)
                        .followRedirects(true)
                        .as(BodyCodec.pipe(WriteStream.newInstance(logoCollector), false))
                        .rxSend()
                        .map(response -> {
                            if (response.statusCode() != HttpStatusCode.OK_200) {
                                log.debug(
                                        "CIMD logo fetch returned {} for uri {}",
                                        response.statusCode(),
                                        logoUri);
                                return Optional.<CachedLogo>empty();
                            }
                            final Buffer body =
                                    logoCollector.body().length() > 0 ? logoCollector.body() : response.bodyAsBuffer();
                            if (body == null || body.length() <= 0 || body.length() > MAX_LOGO_SIZE_BYTES) {
                                return Optional.<CachedLogo>empty();
                            }
                            final byte[] bytes = body.getBytes();
                            final String contentType = resolveContentType(response.getHeader("Content-Type"), bytes);
                            return Optional.of(new CachedLogo(bytes, contentType, metadataTtlSeconds));
                        });
                })
                .retryWhen(new RetryAtMostWithDelay(FETCH_RETRY_ATTEMPTS, FETCH_RETRY_DELAY_MS))
                .onErrorResumeNext(throwable -> {
                    if (throwable instanceof TimeoutException) {
                        log.debug("CIMD logo fetch timed out for uri {}", logoUri);
                    } else if (throwable instanceof BoundedBufferWriteStream.MaxResponseSizeExceededException) {
                        log.debug("CIMD logo fetch exceeded max size for uri {}", logoUri);
                    } else {
                        log.debug("CIMD logo fetch failed for uri {}: {}", logoUri, throwable.getMessage());
                    }
                    return Single.just(Optional.empty());
                });
    }

    private static String resolveContentType(String headerValue, byte[] bytes) {
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue.trim();
        }
        return CimdMetadataDocumentManager.detectMimeType(bytes);
    }

    private static long resolveTimeoutMs(CIMDSettings settings) {
        return Math.max(1L, settings.getFetchTimeoutMs());
    }
}
