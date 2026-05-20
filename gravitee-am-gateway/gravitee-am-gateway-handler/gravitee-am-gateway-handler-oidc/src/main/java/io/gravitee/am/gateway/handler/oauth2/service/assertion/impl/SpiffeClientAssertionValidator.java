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
package io.gravitee.am.gateway.handler.oauth2.service.assertion.impl;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionValidator;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.gateway.handler.oidc.service.spiffe.SpiffeJwtSvidValidator;
import io.gravitee.am.gateway.handler.oidc.service.spiffe.TrustBundleService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.AgentType;
import io.gravitee.am.model.application.SpiffeApplicationSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.util.Optional;

import static io.gravitee.am.gateway.handler.oauth2.service.assertion.impl.JwtAssertionSupport.NOT_VALID;
import static io.gravitee.am.gateway.handler.oauth2.service.assertion.impl.JwtAssertionSupport.parseJwt;
import static io.gravitee.am.gateway.handler.oauth2.service.assertion.impl.JwtAssertionSupport.unableToValidateClient;

/**
 * Validates a SPIFFE JWT-SVID carried as a {@code jwt-spiffe} client assertion.
 * The client is resolved either via {@code clientIdHint} (request {@code client_id})
 * or, when absent, by treating the SPIFFE URI in {@code sub} as the client_id
 * (CIMD-style). The trust domain is derived from the SPIFFE ID and the bundle
 * keys are fetched via {@link TrustBundleService}.
 */
@Slf4j
public class SpiffeClientAssertionValidator implements ClientAssertionValidator {

    private final ClientLookupService clientLookupService;
    private final JWSService jwsService;
    private final OpenIDDiscoveryService openIDDiscoveryService;
    private final Domain domain;
    private final TrustBundleService trustBundleService;
    private final TrustDomainRepository trustDomainRepository;

    public SpiffeClientAssertionValidator(ClientLookupService clientLookupService,
                                          JWSService jwsService,
                                          OpenIDDiscoveryService openIDDiscoveryService,
                                          Domain domain,
                                          TrustBundleService trustBundleService,
                                          TrustDomainRepository trustDomainRepository) {
        this.clientLookupService = clientLookupService;
        this.jwsService = jwsService;
        this.openIDDiscoveryService = openIDDiscoveryService;
        this.domain = domain;
        this.trustBundleService = trustBundleService;
        this.trustDomainRepository = trustDomainRepository;
    }

    @Override
    public String assertionType() {
        return ClientAuthenticationMethod.JWT_SPIFFE;
    }

    @Override
    public Maybe<Client> validate(String assertion, String basePath, String clientIdHint) {
        return parseJwt(assertion).flatMap(jwt -> validateSpiffeAssertion(jwt, basePath, clientIdHint));
    }

    private Maybe<Client> validateSpiffeAssertion(JWT jwt, String basePath, String clientIdHint) {
        if (!(jwt instanceof SignedJWT signedJWT)) {
            return Maybe.error(NOT_VALID);
        }
        final JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException ex) {
            return Maybe.error(NOT_VALID);
        }

        String sub = claims.getSubject();
        String trustDomainName = SpiffeJwtSvidValidator.trustDomainOf(sub);
        if (trustDomainName == null) {
            return Maybe.error(NOT_VALID);
        }
        OpenIDProviderMetadata discovery = openIDDiscoveryService.getConfiguration(basePath);
        if (discovery == null || discovery.getTokenEndpoint() == null) {
            return Maybe.error(new ServerErrorException("Unable to retrieve discovery token endpoint."));
        }
        String tokenEndpoint = discovery.getTokenEndpoint();

        SpiffeDomainSettings settings = Optional.ofNullable(domain.getOidc())
                .map(o -> o.getSpiffeSettings())
                .orElseGet(SpiffeDomainSettings::defaultSettings);
        if (!settings.isEnabled()) {
            return Maybe.error(new InvalidClientException("SPIFFE auth disabled for this domain"));
        }

        String lookupId = clientIdHint != null && !clientIdHint.isBlank() ? clientIdHint : sub;

        return clientLookupService.findByClientId(lookupId)
                .switchIfEmpty(Maybe.error(new InvalidClientException("Unknown client")))
                .flatMap(client -> {
                    if (!ClientAuthenticationMethod.SPIFFE_JWT.equals(client.getTokenEndpointAuthMethod())) {
                        return Maybe.error(new InvalidClientException("Client is not configured for spiffe_jwt"));
                    }
                    SpiffeApplicationSettings spiffe = client.getSpiffeSettings();
                    if (spiffe == null || spiffe.getTrustDomain() == null) {
                        return Maybe.error(new InvalidClientException("Client missing SPIFFE settings"));
                    }
                    return trustDomainRepository.findByName(ReferenceType.DOMAIN, domain.getId(), spiffe.getTrustDomain())
                            .switchIfEmpty(Maybe.error(new InvalidClientException("Trust domain not registered")))
                            .flatMap(td -> {
                                String fail = new SpiffeJwtSvidValidator(settings)
                                        .validate(signedJWT, td, spiffe, tokenEndpoint);
                                if (fail != null) {
                                    log.info("SPIFFE assertion rejected for client {}: {}", client.getClientId(), fail);
                                    return Maybe.error(new InvalidClientException("assertion is not valid: " + fail));
                                }
                                return verifySpiffeSignature(signedJWT, td)
                                        .map(ok -> buildAgentClientIfApplicable(client, sub));
                            });
                });
    }

    private static Client buildAgentClientIfApplicable(Client client, String spiffeId) {
        if (client.isAgentApplication()
                && (client.getAgentType() == AgentType.HOSTED_DELEGATED
                    || client.getAgentType() == AgentType.AUTONOMOUS)) {
            Client instance = new Client(client);
            instance.setAgentInstanceId(spiffeId);
            return instance;
        }
        return client;
    }

    private Maybe<Boolean> verifySpiffeSignature(SignedJWT signedJWT, TrustDomain td) {
        String kid = signedJWT.getHeader().getKeyID();
        if (kid == null || kid.isBlank()) {
            return Maybe.error(new InvalidClientException("SVID missing kid"));
        }
        return trustBundleService.getKey(td, kid)
                .switchIfEmpty(Maybe.error(new InvalidClientException("No matching key in trust bundle for kid: " + kid)))
                .flatMap(jwk -> {
                    if (!jwsService.isValidSignature(signedJWT, jwk)) {
                        return Maybe.error(unableToValidateClient());
                    }
                    return Maybe.just(Boolean.TRUE);
                });
    }
}
