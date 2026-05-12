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

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdLogoCacheService;
import io.gravitee.am.common.oauth2.ClientIds;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataDocumentManager;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataService;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdUriTrustValidator;
import lombok.extern.slf4j.Slf4j;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.CimdMetadataDocumentService;
import io.gravitee.am.service.cimd.CimdValidationRules;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.utils.RetryAtMostWithDelay;
import io.gravitee.am.service.utils.jwk.converter.JWKConverter;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.streams.WriteStream;
import io.vertx.rxjava3.ext.web.codec.BodyCodec;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import io.gravitee.am.service.utils.vertx.BoundedBufferWriteStream;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class CimdMetadataServiceImpl implements CimdMetadataService {

    private static final Pattern CACHE_CONTROL_MAX_AGE = Pattern.compile("(?:^|,)\\s*max-age\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CACHE_CONTROL_NO_STORE = Pattern.compile("(?:^|,)\\s*no-store\\s*(?:,|$)", Pattern.CASE_INSENSITIVE);
    private static final int FETCH_RETRY_ATTEMPTS = 3;
    private static final int FETCH_RETRY_DELAY_MS = 100;
    private static final String DEFAULT_TOKEN_ENDPOINT_AUTH_METHOD = ClientAuthenticationMethod.NONE;
    private static final List<String> DEFAULT_GRANT_TYPES = List.of(GrantType.AUTHORIZATION_CODE);
    private static final List<String> DEFAULT_RESPONSE_TYPES = List.of(ResponseType.CODE);

    private final Domain domain;
    private final WebClient webClient;
    private final CimdMetadataDocumentService cimdMetadataDocumentService;
    private final CimdMetadataDocumentManager cimdMetadataDocumentManager;
    private final CimdUriTrustValidator cimdUriTrustValidator;
    private final CimdLogoCacheService cimdLogoCacheService;

    public CimdMetadataServiceImpl(Domain domain,
                                   WebClient webClient,
                                   CimdMetadataDocumentService cimdMetadataDocumentService,
                                   CimdMetadataDocumentManager cimdMetadataDocumentManager,
                                   CimdUriTrustValidator cimdUriTrustValidator,
                                   CimdLogoCacheService cimdLogoCacheService) {
        this.domain = domain;
        this.webClient = webClient;
        this.cimdMetadataDocumentService = cimdMetadataDocumentService;
        this.cimdMetadataDocumentManager = cimdMetadataDocumentManager;
        this.cimdUriTrustValidator = cimdUriTrustValidator;
        this.cimdLogoCacheService = cimdLogoCacheService;
    }

    @Override
    public Maybe<Client> resolveClient(String clientId, Client templateClient) {
        return Maybe.defer(() -> {
            if (!ClientIds.isUrlShaped(clientId)) {
                return Maybe.empty();
            }

            if (templateClient == null) {
                return Maybe.error(new InvalidClientMetadataException("No template client available for CIMD resolution."));
            }

            final String canonicalId = ClientIds.canonicalize(clientId);
            final CIMDSettings settings = getCimdSettings();
            final URI clientIdUri = cimdUriTrustValidator.parseHttpUrl(canonicalId, "client_id");
            cimdUriTrustValidator.validateTrust(clientIdUri, settings, "client_id");

            return cimdMetadataDocumentManager.resolve(canonicalId)
                    .flatMapMaybe(opt -> {
                        if (opt.isEmpty()) {
                            return Maybe.empty();
                        }
                        final var doc = opt.get();
                        final JsonObject docMetadata = new JsonObject(doc.getMetadata());
                        cimdLogoCacheService.prefetchLogoAsync(canonicalId, doc.getLogoUri(),
                                CimdMetadataDocumentManager.remainingTtlSeconds(doc), settings);
                        return Maybe.just(synthesizeClient(canonicalId, templateClient, docMetadata));
                    })
                    .switchIfEmpty(
                            Maybe.defer(() -> {
                                log.debug("CIMD cache miss, fetching from origin for domain={}, clientId={}", domain.getId(), canonicalId);
                                return cimdUriTrustValidator.validateResolvableHost(clientIdUri.getHost(), "client_id", settings)
                                        .andThen(fetchMetadataWithTtl(canonicalId, settings))
                                        .flatMap(fetchResult -> validateJWKs(fetchResult, settings)
                                                .andThen(Single.just(fetchResult)))
                                        .flatMap(fetchResult -> {
                                            final FetchResult.CacheRequirements cache = fetchResult.cacheRequirements();
                                            if (!cache.noCache()) {
                                                cimdMetadataDocumentManager.put(canonicalId, fetchResult.json(), cache.ttl());
                                                cimdLogoCacheService.prefetchLogoAsync(canonicalId, fetchResult.json().getString("logo_uri"), cache.ttl().toSeconds(), settings);
                                                cimdMetadataDocumentService
                                                        .upsert(domain, canonicalId, fetchResult.json().encode(), cache.ttl())
                                                        .subscribe(
                                                                doc -> cimdMetadataDocumentManager.put(canonicalId, doc),
                                                                err -> log.warn("CIMD failed to persist metadata for domain={}, clientId={}: {}", domain.getId(), canonicalId, err.getMessage())
                                                        );
                                            }
                                            return Single.just(synthesizeClient(canonicalId, templateClient, fetchResult.json()));
                                        })
                                        .toMaybe();
                            })
                    );
        });
    }

    private Completable validateJWKs(FetchResult fetchResult, CIMDSettings settings) {
        String jwksUri = fetchResult.json().getString("jwks_uri");
        if (jwksUri == null || jwksUri.trim().isEmpty()) {
            return Completable.complete();
        }
        final URI jwksURI = cimdUriTrustValidator.parseHttpUrl(jwksUri, "jwks_uri");
        cimdUriTrustValidator.validateTrust(jwksURI, settings, "jwks_uri");
        return cimdUriTrustValidator.validateResolvableHost(jwksURI.getHost(), "jwks_uri", settings);
    }

    private CIMDSettings getCimdSettings() {
        if (domain == null || domain.getOidc() == null || domain.getOidc().getCimdSettings() == null) {
            throw new InvalidClientMetadataException("CIMD settings are not configured for this domain.");
        }
        return domain.getOidc().getCimdSettings();
    }

    private Single<FetchResult> fetchMetadataWithTtl(String clientId, CIMDSettings settings) {
        final long timeoutMs = resolveTimeoutMs(settings);
        final long maxResponseSize = Math.max(0L, settings.getMaxResponseSizeKb()) * 1024L;
        final long maxCacheTtlSeconds = resolveCacheTtlSeconds(settings);

        return Single.defer(() -> {
                    final var responseCollector = new BoundedBufferWriteStream(maxResponseSize);

                    return webClient.getAbs(clientId)
                            .timeout(timeoutMs)
                            .followRedirects(false)
                            .as(BodyCodec.pipe(WriteStream.newInstance(responseCollector), false))
                            .rxSend()
                            .flatMap(response -> {
                                if (response.statusCode() != HttpStatusCode.OK_200) {
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
                                    final JsonObject json = new JsonObject(body);
                                    return Single.just(new FetchResult(json, resolveCacheRequirements(response, maxCacheTtlSeconds)));
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

    private static FetchResult.CacheRequirements resolveCacheRequirements(HttpResponse<?> response, long maxCacheTtlSeconds) {
        final String cacheControl = response.getHeader("Cache-Control");
        if (cacheControl != null) {
            if (CACHE_CONTROL_NO_STORE.matcher(cacheControl).find()) {
                return new FetchResult.CacheRequirements(Duration.ZERO, true);
            }
            Matcher m = CACHE_CONTROL_MAX_AGE.matcher(cacheControl);
            if (m.find()) {
                try {
                    long serverMaxAge = Long.parseLong(m.group(1));
                    return new FetchResult.CacheRequirements(
                            Duration.ofSeconds(Math.min(serverMaxAge, maxCacheTtlSeconds)), false);
                } catch (NumberFormatException ignored) {
                    // fall through to configured TTL
                }
            }
        }
        return new FetchResult.CacheRequirements(Duration.ofSeconds(maxCacheTtlSeconds), false);
    }

    private Client synthesizeClient(String clientId, Client templateClient, JsonObject metadata) {
        final String metadataClientId = metadata.getString("client_id");
        if (!clientId.equals(ClientIds.canonicalize(metadataClientId))) {
            throw new InvalidClientMetadataException("client_id in metadata does not match requested client_id.");
        }

        final List<String> redirectUris = readRequiredStringArray(metadata, "redirect_uris");

        final String tokenEndpointAuthMethod = metadata.getString(
                "token_endpoint_auth_method",
                DEFAULT_TOKEN_ENDPOINT_AUTH_METHOD
        );
        if (CimdValidationRules.FORBIDDEN_SECRET_BASED_AUTH_METHODS.contains(tokenEndpointAuthMethod)) {
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

        final List<String> grantTypes = intersect(
                readOptionalStringArray(metadata, "grant_types", DEFAULT_GRANT_TYPES),
                templateClient.getAuthorizedGrantTypes());
        final List<String> responseTypes = intersect(
                readOptionalStringArray(metadata, "response_types", DEFAULT_RESPONSE_TYPES),
                templateClient.getResponseTypes());
        final List<ApplicationScopeSettings> scopes = intersectScopes(
                metadata.getString("scope"),
                templateClient.getScopeSettings());

        final Client synthesizedClient;
        try {
            synthesizedClient = templateClient.clone();
        } catch (CloneNotSupportedException ex) {
            throw new InvalidClientMetadataException("Unable to clone template client.");
        }

        synthesizedClient.setClientId(clientId);
        synthesizedClient.setRedirectUris(redirectUris);
        synthesizedClient.setTokenEndpointAuthMethod(tokenEndpointAuthMethod);
        synthesizedClient.setAuthorizedGrantTypes(grantTypes);
        synthesizedClient.setResponseTypes(responseTypes);
        synthesizedClient.setScopeSettings(scopes);
        synthesizedClient.setClientName(metadata.getString("client_name", synthesizedClient.getClientName()));
        final String logoUri = metadata.getString("logo_uri");
        if (logoUri != null && !logoUri.isBlank()) {
            synthesizedClient.setLogoUri(logoUri);
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

        applyExtendedMetadata(metadata, synthesizedClient);

        synthesizedClient.setClientSecret(null);
        synthesizedClient.setClientSecrets(List.of());
        synthesizedClient.setSecretSettings(List.of());
        synthesizedClient.setTemplate(false);
        synthesizedClient.setDomain(domain.getId());

        synthesizedClient.setCimdMetadataHash(calculateMetadataHash(metadata.encode()));

        return synthesizedClient;
    }

    private void applyExtendedMetadata(JsonObject metadata, Client client) {
        applyOptionalString(metadata, "application_type", client::setApplicationType);
        applyOptionalString(metadata, "subject_type", client::setSubjectType);
        applyOptionalString(metadata, "sector_identifier_uri", client::setSectorIdentifierUri);
        applyOptionalString(metadata, "id_token_signed_response_alg", client::setIdTokenSignedResponseAlg);
        applyOptionalString(metadata, "client_uri", client::setClientUri);
        applyOptionalString(metadata, "policy_uri", client::setPolicyUri);
        applyOptionalString(metadata, "tos_uri", client::setTosUri);
        applyOptionalString(metadata, "software_id", client::setSoftwareId);
        applyOptionalString(metadata, "software_version", client::setSoftwareVersion);
        applyOptionalString(metadata, "software_statement", client::setSoftwareStatement);
        applyOptionalString(metadata, "tls_client_auth_subject_dn", client::setTlsClientAuthSubjectDn);
        applyOptionalString(metadata, "tls_client_auth_san_dns", client::setTlsClientAuthSanDns);
        applyOptionalString(metadata, "tls_client_auth_san_uri", client::setTlsClientAuthSanUri);
        applyOptionalString(metadata, "tls_client_auth_san_ip", client::setTlsClientAuthSanIp);
        applyOptionalString(metadata, "tls_client_auth_san_email", client::setTlsClientAuthSanEmail);
        applyOptionalString(metadata, "backchannel_token_delivery_mode", client::setBackchannelTokenDeliveryMode);
        applyOptionalString(metadata, "backchannel_client_notification_endpoint", client::setBackchannelClientNotificationEndpoint);
        applyOptionalString(metadata, "backchannel_authentication_request_signing_alg", client::setBackchannelAuthRequestSignAlg);

        Boolean tlsBoundTokens = optionalBoolean(metadata, "tls_client_certificate_bound_access_tokens");
        if (tlsBoundTokens != null) {
            client.setTlsClientCertificateBoundAccessTokens(tlsBoundTokens);
        }
        Boolean userCodeParam = optionalBoolean(metadata, "backchannel_user_code_parameter");
        if (userCodeParam != null) {
            client.setBackchannelUserCodeParameter(userCodeParam);
        }
        Boolean requirePar = optionalBoolean(metadata, "require_pushed_authorization_requests");
        if (requirePar != null) {
            client.setRequireParRequest(requirePar);
        }

        List<String> postLogoutRedirectUris = readOptionalStringArray(metadata, "post_logout_redirect_uris");
        if (postLogoutRedirectUris != null && !postLogoutRedirectUris.isEmpty()) {
            client.setPostLogoutRedirectUris(postLogoutRedirectUris);
        }
        List<String> contacts = readOptionalStringArray(metadata, "contacts");
        if (contacts != null && !contacts.isEmpty()) {
            client.setContacts(contacts);
        }
        List<String> requestUris = readOptionalStringArray(metadata, "request_uris");
        if (requestUris != null && !requestUris.isEmpty()) {
            client.setRequestUris(requestUris);
        }
    }

    private static void applyOptionalString(JsonObject metadata, String key, java.util.function.Consumer<String> setter) {
        String value = metadata.getString(key);
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private static Boolean optionalBoolean(JsonObject metadata, String key) {
        Object raw = metadata.getValue(key);
        return (raw instanceof Boolean b) ? b : null;
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

    private List<String> readOptionalStringArray(JsonObject metadata, String key, List<String> defaultValue) {
        final List<String> values = readOptionalStringArray(metadata, key);
        return values != null ? values : defaultValue;
    }

    private static List<String> intersect(List<String> requested, List<String> allowed) {
        if (requested == null || requested.isEmpty() || allowed == null || allowed.isEmpty()) {
            return List.of();
        }
        final var allowedSet = new HashSet<>(allowed);
        return requested.stream().filter(allowedSet::contains).toList();
    }

    private static List<ApplicationScopeSettings> intersectScopes(
            String requested, List<ApplicationScopeSettings> templateScopes) {
        if (requested == null || requested.isBlank() || templateScopes == null || templateScopes.isEmpty()) {
            return templateScopes;
        }
        final Set<String> scopes = new HashSet<>(Arrays.asList(requested.split("\\s+")));
        return templateScopes.stream()
                .filter(s -> scopes.contains(s.getScope()))
                .toList();
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

    private static long resolveTimeoutMs(CIMDSettings settings) {
        return Math.max(1L, settings.getFetchTimeoutMs());
    }

    private static long resolveCacheTtlSeconds(CIMDSettings settings) {
        return Math.max(1L, settings.getCacheTtlSeconds());
    }

    private static String calculateMetadataHash(String metadataJson) {
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                    .digest(metadataJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private record FetchResult(JsonObject json, CacheRequirements cacheRequirements) {
        private record CacheRequirements(Duration ttl, boolean noCache) {}
    }
}
