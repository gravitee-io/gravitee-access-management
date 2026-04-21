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
package io.gravitee.am.gateway.handler.common.client.cimd.impl;

import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.utils.RetryAtMostWithDelay;
import io.gravitee.am.service.utils.jwk.converter.JWKConverter;
import io.gravitee.am.service.utils.vertx.BoundedBufferWriteStream;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.streams.WriteStream;
import io.vertx.rxjava3.ext.web.codec.BodyCodec;
import io.vertx.rxjava3.ext.web.client.WebClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class CimdMetadataServiceImpl implements CimdMetadataService {

    private static final Pattern URL_SHAPED_CLIENT_ID = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
    private static final Set<String> FORBIDDEN_SECRET_BASED_AUTH_METHODS = Set.of(
            ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
            ClientAuthenticationMethod.CLIENT_SECRET_POST,
            ClientAuthenticationMethod.CLIENT_SECRET_JWT
    );
    private static final int FETCH_RETRY_ATTEMPTS = 3;
    private static final int FETCH_RETRY_DELAY_MS = 100;

    private final Domain domain;
    private final WebClient webClient;

    public CimdMetadataServiceImpl(Domain domain, WebClient webClient) {
        this.domain = domain;
        this.webClient = webClient;
    }

    @Override
    public Maybe<Client> resolveClient(String clientId, Client templateClient) {
        return Maybe.defer(() -> {
            if (clientId == null || !URL_SHAPED_CLIENT_ID.matcher(clientId).find()) {
                return Maybe.empty();
            }

            if (templateClient == null) {
                return Maybe.error(new InvalidClientMetadataException("No template client available for CIMD resolution."));
            }

            final CIMDSettings settings = getCimdSettings();
            final URI clientIdUri = toUri(clientId);
            validateUrlTrust(clientIdUri, settings);

            return fetchMetadata(clientId, settings)
                    .map(metadata -> synthesizeClient(clientId, templateClient, metadata))
                    .toMaybe();
        });
    }

    private CIMDSettings getCimdSettings() {
        if (domain == null || domain.getOidc() == null || domain.getOidc().getCimdSettings() == null) {
            throw new InvalidClientMetadataException("CIMD settings are not configured for this domain.");
        }
        return domain.getOidc().getCimdSettings();
    }

    private URI toUri(String clientId) {
        try {
            return UriBuilder.fromHttpUrl(clientId).build();
        } catch (IllegalArgumentException | URISyntaxException ex) {
            throw new InvalidClientMetadataException("client_id is not a valid URL.");
        }
    }

    private void validateUrlTrust(URI clientIdUri, CIMDSettings settings) {
        if (!settings.isAllowUnsecuredHttpUri() && "http".equalsIgnoreCase(clientIdUri.getScheme())) {
            throw new InvalidClientMetadataException("Unsecured HTTP client_id is not allowed.");
        }

        final String host = clientIdUri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidClientMetadataException("client_id host is missing.");
        }

        if (!isAllowedDomain(host, settings.getAllowedDomains())) {
            throw new InvalidClientMetadataException("client_id host is not in allowed domains.");
        }
    }

    private boolean isAllowedDomain(String host, List<String> allowedDomains) {
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            return true;
        }

        final String normalizedHost = host.toLowerCase(Locale.ROOT);

        return allowedDomains.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .map(pattern -> pattern.toLowerCase(Locale.ROOT))
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

    private Single<JsonObject> fetchMetadata(String clientId, CIMDSettings settings) {
        final long timeoutMs = Math.max(1L, settings.getFetchTimeoutMs());
        final long maxResponseSize = Math.max(0L, settings.getMaxResponseSizeKb()) * 1024L;

        return Single.defer(() -> {
                    final var responseCollector = new BoundedBufferWriteStream(maxResponseSize);

                    return webClient.getAbs(clientId)
                            .timeout(timeoutMs)
                            .followRedirects(false)
                            .as(BodyCodec.pipe(WriteStream.newInstance(responseCollector), false))
                            .rxSend()
                            .flatMap(response -> {
                                if (response.statusCode() != 200) {
                                    return Single.error(new InvalidClientMetadataException("Client metadata endpoint returned HTTP " + response.statusCode() + "."));
                                }

                                final Buffer body = responseCollector.body().length() > 0 ? responseCollector.body() : response.bodyAsBuffer();
                                if (body == null) {
                                    return Single.error(new InvalidClientMetadataException("Client metadata endpoint returned an empty body."));
                                }

                                if (maxResponseSize > 0 && body.length() > maxResponseSize) {
                                    return Single.error(new InvalidClientMetadataException("Client metadata response exceeds configured max size."));
                                }

                                try {
                                    return Single.just(new JsonObject(body));
                                } catch (Exception ex) {
                                    return Single.error(new InvalidClientMetadataException("Client metadata response is not valid JSON."));
                                }
                            });
                })
                .retryWhen(new RetryAtMostWithDelay(FETCH_RETRY_ATTEMPTS, FETCH_RETRY_DELAY_MS))
                .onErrorResumeNext(throwable -> {
                    if (throwable instanceof InvalidClientMetadataException invalidClientMetadataException) {
                        return Single.error(invalidClientMetadataException);
                    }
                    if(throwable instanceof BoundedBufferWriteStream.MaxResponseSizeExceededException ex) {
                        return Single.error(new InvalidClientMetadataException(ex.getMessage()));
                    }
                    if (throwable instanceof TimeoutException) {
                        return Single.error(new InvalidClientMetadataException("Client metadata fetch timed out."));
                    }
                    return Single.error(new InvalidClientMetadataException("Unable to fetch client metadata."));
                });
    }

    private Client synthesizeClient(String clientId, Client templateClient, JsonObject metadata) {
        final String metadataClientId = metadata.getString("client_id");
        if (!clientId.equals(metadataClientId)) {
            throw new InvalidClientMetadataException("client_id in metadata does not match requested client_id.");
        }

        final List<String> redirectUris = readRequiredStringArray(metadata, "redirect_uris");

        final String tokenEndpointAuthMethod = metadata.getString(
                "token_endpoint_auth_method",
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC
        );
        if (FORBIDDEN_SECRET_BASED_AUTH_METHODS.contains(tokenEndpointAuthMethod)) {
            throw new InvalidClientMetadataException("Secret-based token_endpoint_auth_method is not allowed for CIMD clients.");
        }

        if (metadata.containsKey("client_secret") || metadata.containsKey("client_secret_expires_at")) {
            throw new InvalidClientMetadataException("client_secret metadata is not allowed for CIMD clients.");
        }

        final String jwksUri = metadata.getString("jwks_uri");
        final Object jwksRaw = metadata.getValue("jwks");
        final boolean hasJwks = jwksRaw instanceof JsonObject;
        final boolean hasJwksUri = jwksUri != null && !jwksUri.isBlank();

        if (ClientAuthenticationMethod.PRIVATE_KEY_JWT.equals(tokenEndpointAuthMethod) && !hasJwks && !hasJwksUri) {
            throw new InvalidClientMetadataException("private_key_jwt requires jwks or jwks_uri.");
        }

        final List<String> grantTypes = readOptionalStringArray(metadata, "grant_types");
        final List<String> responseTypes = readOptionalStringArray(metadata, "response_types");

        final Client synthesizedClient;
        try {
            synthesizedClient = templateClient.clone();
        } catch (CloneNotSupportedException ex) {
            throw new InvalidClientMetadataException("Unable to clone template client.");
        }

        synthesizedClient.setClientId(clientId);
        synthesizedClient.setRedirectUris(redirectUris);
        synthesizedClient.setTokenEndpointAuthMethod(tokenEndpointAuthMethod);
        synthesizedClient.setClientName(metadata.getString("client_name", synthesizedClient.getClientName()));
        if (grantTypes != null) {
            synthesizedClient.setAuthorizedGrantTypes(grantTypes);
        }
        if (responseTypes != null) {
            synthesizedClient.setResponseTypes(responseTypes);
        }
        if (metadata.containsKey("jwks_uri")) {
            synthesizedClient.setJwksUri(jwksUri);
        }
        if (metadata.containsKey("jwks")) {
            if (!hasJwks) {
                throw new InvalidClientMetadataException("jwks must be a JSON object.");
            }
            synthesizedClient.setJwks(toModelJwkSet((JsonObject) jwksRaw));
        }

        synthesizedClient.setClientSecret(null);
        synthesizedClient.setClientSecrets(List.of());
        synthesizedClient.setSecretSettings(List.of());
        synthesizedClient.setTemplate(false);
        synthesizedClient.setDomain(domain.getId());
        return synthesizedClient;
    }

    private List<String> readRequiredStringArray(JsonObject metadata, String key) {
        final List<String> values = readOptionalStringArray(metadata, key);
        if (values == null || values.isEmpty()) {
            throw new InvalidClientMetadataException("Missing or invalid " + key + ".");
        }
        return values;
    }

    private List<String> readOptionalStringArray(JsonObject metadata, String key) {
        if (!metadata.containsKey(key)) {
            return null;
        }
        final Object raw = metadata.getValue(key);
        if (!(raw instanceof JsonArray jsonArray)) {
            throw new InvalidClientMetadataException("Invalid " + key + ".");
        }
        return jsonArray.stream().map(item -> {
            if (!(item instanceof String value) || value.isBlank()) {
                throw new InvalidClientMetadataException("Invalid " + key + ".");
            }
            return value;
        }).toList();
    }

    private JWKSet toModelJwkSet(JsonObject jwksAsJson) {
        try {
            final com.nimbusds.jose.jwk.JWKSet parsed = com.nimbusds.jose.jwk.JWKSet.parse(jwksAsJson.encode());
            final JWKSet result = new JWKSet();
            result.setKeys(parsed.getKeys().stream().map(JWKConverter::convert).toList());
            return result;
        } catch (ParseException ex) {
            throw new InvalidClientMetadataException("Unable to parse jwks content.");
        }
    }
}
