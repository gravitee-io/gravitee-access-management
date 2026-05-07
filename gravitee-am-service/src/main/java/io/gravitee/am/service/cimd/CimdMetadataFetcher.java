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
package io.gravitee.am.service.cimd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.http.WebClientBuilder;
import io.gravitee.am.service.model.CimdClientMetadata;
import io.gravitee.am.service.utils.vertx.BoundedBufferWriteStream;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.streams.WriteStream;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import io.vertx.rxjava3.ext.web.codec.BodyCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches and validates a CIMD (Client Identity Metadata Document) URL from the management API
 * context, returning a {@link CimdClientMetadata} suitable for surfacing in the application creation UI.
 *
 * <p>Mirrors the gateway-runtime trust + validation rules in
 * {@code CimdMetadataServiceImpl} / {@code CimdUriTrustValidator}, using the Vert.x
 * {@link WebClient} so HTTP traffic flows through the same stack as the rest of the platform.</p>
 */
@Component
public class CimdMetadataFetcher {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern URL_SHAPED = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
    private static final Pattern CACHE_CONTROL_MAX_AGE = Pattern.compile("(?:^|,)\\s*max-age\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CACHE_CONTROL_NO_STORE = Pattern.compile("(?:^|,)\\s*no-store\\s*(?:,|$)", Pattern.CASE_INSENSITIVE);
    private final WebClient webClient;

    @Autowired
    public CimdMetadataFetcher(Vertx vertx, WebClientBuilder webClientBuilder) {
        this.webClient = webClientBuilder.createWebClient(vertx);
    }

    // Visible for testing.
    CimdMetadataFetcher(WebClient webClient) {
        this.webClient = webClient;
    }

    public Single<CimdClientMetadata> fetchAndValidate(Domain domain, String rawUrl) {
        return Single.defer(() -> {
            if (domain == null || domain.getOidc() == null || domain.getOidc().getCimdSettings() == null) {
                return Single.error(new InvalidClientMetadataException("CIMD settings are not configured for this domain."));
            }
            final CIMDSettings settings = domain.getOidc().getCimdSettings();
            if (!settings.isEnabled()) {
                return Single.error(new InvalidClientMetadataException("CIMD is not enabled for this domain."));
            }
            if (rawUrl == null || rawUrl.isBlank() || !URL_SHAPED.matcher(rawUrl).find()) {
                return Single.error(new InvalidClientMetadataException("CIMD url must be an http(s) URL."));
            }

            final URI uri;
            try {
                uri = parseHttpUrl(rawUrl);
                validateTrust(uri, settings);
                if (!settings.isAllowPrivateIpAddress()) {
                    assertResolvableAndNotPrivate(uri.getHost());
                }
            } catch (InvalidClientMetadataException ex) {
                return Single.error(ex);
            }

            return fetch(rawUrl, settings)
                    .map(fetched -> {
                        final JsonNode metadata = parseJson(fetched.body());
                        validateMetadata(metadata);
                        return toPreview(rawUrl, metadata, fetched);
                    });
        });
    }

    private URI parseHttpUrl(String value) {
        try {
            return UriBuilder.fromHttpUrl(value).build();
        } catch (IllegalArgumentException | URISyntaxException ex) {
            throw new InvalidClientMetadataException("CIMD url is not a valid URL.");
        }
    }

    private void validateTrust(URI uri, CIMDSettings settings) {
        if (!settings.isAllowUnsecuredHttpUri() && "http".equalsIgnoreCase(uri.getScheme())) {
            throw new InvalidClientMetadataException("Unsecured HTTP CIMD url is not allowed.");
        }
        final String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidClientMetadataException("CIMD url host is missing.");
        }
        if (!isAllowedDomain(host, settings.getAllowedDomains())) {
            throw new InvalidClientMetadataException("CIMD url host is not in allowed domains.");
        }
        if (!settings.isAllowPrivateIpAddress() && UriBuilder.isPrivateOrReservedIpLiteral(host)) {
            throw new InvalidClientMetadataException("CIMD url resolves to a private or reserved IP address.");
        }
    }

    private void assertResolvableAndNotPrivate(String host) {
        if (host == null || UriBuilder.isIpLiteral(host)) {
            return;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                        || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                    throw new InvalidClientMetadataException("CIMD url resolves to a private or reserved IP address.");
                }
            }
        } catch (UnknownHostException ignored) {
            // Defer the failure to the HTTP fetch which produces a clearer error.
        }
    }

    private static boolean isAllowedDomain(String host, List<String> allowedDomains) {
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            return true;
        }
        final String normalizedHost = host.toLowerCase(Locale.ROOT);
        return allowedDomains.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(pattern -> {
                    if (pattern.startsWith("*.")) {
                        final String suffix = pattern.substring(2);
                        if (suffix.isEmpty() || !normalizedHost.endsWith("." + suffix)) {
                            return false;
                        }
                        final String subdomain = normalizedHost.substring(0, normalizedHost.length() - suffix.length() - 1);
                        return !subdomain.isEmpty();
                    }
                    return normalizedHost.equals(pattern);
                });
    }

    private Single<FetchResult> fetch(String url, CIMDSettings settings) {
        final long timeoutMs = Math.max(1L, settings.getFetchTimeoutMs());
        final long maxBytes = Math.max(0L, settings.getMaxResponseSizeKb()) * 1024L;
        final long maxCacheTtlSeconds = Math.max(1L, settings.getCacheTtlSeconds());

        return Single.defer(() -> {
                    final BoundedBufferWriteStream collector = new BoundedBufferWriteStream(maxBytes > 0 ? maxBytes : 1L);
                    return webClient.getAbs(url)
                            .timeout(timeoutMs)
                            .followRedirects(false)
                            .putHeader("Accept", "application/json")
                            .as(BodyCodec.pipe(WriteStream.newInstance(collector), false))
                            .rxSend()
                            .flatMap(response -> {
                                if (response.statusCode() != HttpStatusCode.OK_200) {
                                    return Single.error(new InvalidClientMetadataException(
                                            "Client metadata endpoint returned HTTP " + response.statusCode() + "."));
                                }
                                final Buffer body = collector.body().length() > 0 ? collector.body() : response.bodyAsBuffer();
                                if (body == null || body.length() == 0) {
                                    return Single.error(new InvalidClientMetadataException("Client metadata endpoint returned an empty body."));
                                }
                                if (maxBytes > 0 && body.length() > maxBytes) {
                                    return Single.error(new InvalidClientMetadataException("Client metadata response exceeds configured max size."));
                                }
                                return Single.just(new FetchResult(body.getBytes(), resolveTtl(response, maxCacheTtlSeconds)));
                            });
                })
                .onErrorResumeNext(throwable -> {
                    if (throwable instanceof InvalidClientMetadataException ex) {
                        return Single.error(ex);
                    }
                    if (throwable instanceof BoundedBufferWriteStream.MaxResponseSizeExceededException ex) {
                        return Single.error(new InvalidClientMetadataException(ex.getMessage()));
                    }
                    if (throwable instanceof TimeoutException) {
                        return Single.error(new InvalidClientMetadataException("Client metadata fetch timed out."));
                    }
                    return Single.error(new InvalidClientMetadataException("Unable to fetch client metadata."));
                });
    }

    private Duration resolveTtl(HttpResponse<?> response, long maxCacheTtlSeconds) {
        final String cacheControl = response.getHeader("Cache-Control");
        if (cacheControl != null) {
            if (CACHE_CONTROL_NO_STORE.matcher(cacheControl).find()) {
                return Duration.ZERO;
            }
            Matcher m = CACHE_CONTROL_MAX_AGE.matcher(cacheControl);
            if (m.find()) {
                try {
                    long serverMaxAge = Long.parseLong(m.group(1));
                    return Duration.ofSeconds(Math.min(serverMaxAge, maxCacheTtlSeconds));
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        return Duration.ofSeconds(maxCacheTtlSeconds);
    }

    private JsonNode parseJson(byte[] body) {
        try {
            return MAPPER.readTree(body);
        } catch (IOException ex) {
            throw new InvalidClientMetadataException("Client metadata response is not valid JSON.");
        }
    }

    private void validateMetadata(JsonNode metadata) {
        if (!metadata.isObject()) {
            throw new InvalidClientMetadataException("Client metadata response is not a JSON object.");
        }

        final JsonNode redirectUris = metadata.get("redirect_uris");
        if (redirectUris == null || !redirectUris.isArray() || redirectUris.isEmpty()) {
            throw new InvalidClientMetadataException("Missing or invalid redirect_uris.");
        }
        for (JsonNode item : redirectUris) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new InvalidClientMetadataException("Invalid redirect_uris.");
            }
        }

        if (metadata.has("client_secret") || metadata.has("client_secret_expires_at")) {
            throw new InvalidClientMetadataException("client_secret metadata is not allowed for CIMD clients.");
        }

        final String tokenEndpointAuthMethod = optionalText(metadata, "token_endpoint_auth_method");
        if (tokenEndpointAuthMethod != null && CimdValidationRules.FORBIDDEN_SECRET_BASED_AUTH_METHODS.contains(tokenEndpointAuthMethod)) {
            throw new InvalidClientMetadataException("Secret-based token_endpoint_auth_method is not allowed for CIMD clients.");
        }

        final boolean hasJwks = metadata.has("jwks") && metadata.get("jwks").isObject();
        final String jwksUri = optionalText(metadata, "jwks_uri");
        final boolean hasJwksUri = jwksUri != null && !jwksUri.isBlank();
        if (ClientAuthenticationMethod.PRIVATE_KEY_JWT.equals(tokenEndpointAuthMethod) && !hasJwks && !hasJwksUri) {
            throw new InvalidClientMetadataException("private_key_jwt requires jwks or jwks_uri.");
        }
    }

    private CimdClientMetadata toPreview(String url, JsonNode metadata, FetchResult fetched) {
        final String docClientId = optionalText(metadata, "client_id");
        final String docClientName = optionalText(metadata, "client_name");
        final boolean hasInlineJwks = metadata.has("jwks") && metadata.get("jwks").isObject();

        final CimdClientMetadata.Missing missing = new CimdClientMetadata.Missing(
                docClientId == null || docClientId.isBlank(),
                docClientName == null || docClientName.isBlank()
        );

        final String body;
        try {
            body = MAPPER.writeValueAsString(metadata);
        } catch (IOException ex) {
            throw new InvalidClientMetadataException("Unable to serialise client metadata response.");
        }

        return new CimdClientMetadata(
                url,
                docClientId,
                docClientName,
                readStringArray(metadata, "redirect_uris"),
                readStringArray(metadata, "post_logout_redirect_uris"),
                readScopes(metadata),
                readStringArray(metadata, "grant_types"),
                readStringArray(metadata, "response_types"),
                readStringArray(metadata, "contacts"),
                readStringArray(metadata, "request_uris"),
                optionalText(metadata, "token_endpoint_auth_method"),
                optionalText(metadata, "application_type"),
                optionalText(metadata, "subject_type"),
                optionalText(metadata, "sector_identifier_uri"),
                optionalText(metadata, "id_token_signed_response_alg"),
                optionalText(metadata, "logo_uri"),
                optionalText(metadata, "client_uri"),
                optionalText(metadata, "policy_uri"),
                optionalText(metadata, "tos_uri"),
                optionalText(metadata, "jwks_uri"),
                hasInlineJwks,
                optionalText(metadata, "software_id"),
                optionalText(metadata, "software_version"),
                optionalText(metadata, "software_statement"),
                optionalText(metadata, "tls_client_auth_subject_dn"),
                optionalText(metadata, "tls_client_auth_san_dns"),
                optionalText(metadata, "tls_client_auth_san_uri"),
                optionalText(metadata, "tls_client_auth_san_ip"),
                optionalText(metadata, "tls_client_auth_san_email"),
                optionalBoolean(metadata, "tls_client_certificate_bound_access_tokens"),
                optionalText(metadata, "backchannel_token_delivery_mode"),
                optionalText(metadata, "backchannel_client_notification_endpoint"),
                optionalText(metadata, "backchannel_authentication_request_signing_alg"),
                optionalBoolean(metadata, "backchannel_user_code_parameter"),
                missing,
                body,
                fetched.ttl()
        );
    }

    private static Boolean optionalBoolean(JsonNode node, String key) {
        final JsonNode value = node.get(key);
        return (value != null && value.isBoolean()) ? value.asBoolean() : null;
    }

    private static String optionalText(JsonNode node, String key) {
        final JsonNode value = node.get(key);
        return (value != null && value.isTextual()) ? value.asText() : null;
    }

    private static List<String> readStringArray(JsonNode node, String key) {
        final JsonNode value = node.get(key);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        final List<String> list = new ArrayList<>();
        for (Iterator<JsonNode> it = value.elements(); it.hasNext(); ) {
            JsonNode item = it.next();
            if (item.isTextual()) {
                list.add(item.asText());
            }
        }
        return list;
    }

    private static List<String> readScopes(JsonNode metadata) {
        final String raw = optionalText(metadata, "scope");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\s+")).filter(s -> !s.isBlank()).toList();
    }

    private record FetchResult(byte[] body, Duration ttl) {}
}
