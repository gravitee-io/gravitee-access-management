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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.AgentType;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentJwtBearerClientAssertionValidatorTest {

    private static final String BLUEPRINT_ID = "blueprint-1";
    private static final String AGENT_INSTANCE_ID = "agent-42";
    private static final String AUDIENCE = "https://am.example.org/oauth/token";
    private static final String BASE_PATH = "/";
    private static final String KID = "kid-1";

    @Mock
    private ClientLookupService clientLookupService;
    @Mock
    private JWKService jwkService;
    @Mock
    private JWSService jwsService;
    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;
    @Mock
    private Domain domain;
    @Mock
    private OpenIDProviderMetadata discovery;

    private RSAPrivateKey privateKey;
    private RSAKey jwk;
    private AgentJwtBearerClientAssertionValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKey = (RSAPrivateKey) pair.getPrivate();

        jwk = new RSAKey();
        jwk.setKid(KID);

        validator = new AgentJwtBearerClientAssertionValidator(
                clientLookupService, jwkService, jwsService, openIDDiscoveryService, domain);
    }

    /** Stubs the discovery + FAPI calls that run on every path past the claims/exp checks. */
    private void stubDiscoveryAndFapi() {
        when(openIDDiscoveryService.getConfiguration(BASE_PATH)).thenReturn(discovery);
        when(discovery.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(domain.usePlainFapiProfile()).thenReturn(false);
    }

    @Test
    void accepts_autonomousBlueprintWithPrivateKeyJwt() throws Exception {
        Client blueprint = agentBlueprint(AgentType.AUTONOMOUS, ClientAuthenticationMethod.PRIVATE_KEY_JWT, true);
        stubLookupAndJwk(blueprint, true);

        validator.validate(workloadJwt(BLUEPRINT_ID, AGENT_INSTANCE_ID), BASE_PATH, null).test()
                .assertNoErrors()
                .assertValue(c -> BLUEPRINT_ID.equals(c.getClientId())
                        && AGENT_INSTANCE_ID.equals(c.getAgentInstanceId())
                        && c.isAgentApplication());
    }

    @Test
    void accepts_hostedDelegatedBlueprintWithClientSecretJwt() throws Exception {
        Client blueprint = agentBlueprint(AgentType.HOSTED_DELEGATED, ClientAuthenticationMethod.CLIENT_SECRET_JWT, true);
        stubLookupAndJwk(blueprint, true);

        validator.validate(workloadJwt(BLUEPRINT_ID, AGENT_INSTANCE_ID), BASE_PATH, null).test()
                .assertNoErrors()
                .assertValue(c -> AGENT_INSTANCE_ID.equals(c.getAgentInstanceId()));
    }

    @Test
    void rejects_userEmbeddedAgentType() throws Exception {
        stubDiscoveryAndFapi();
        Client blueprint = agentBlueprint(AgentType.USER_EMBEDDED, ClientAuthenticationMethod.PRIVATE_KEY_JWT, true);
        when(clientLookupService.findByClientId(BLUEPRINT_ID)).thenReturn(Maybe.just(blueprint));

        validator.validate(workloadJwt(BLUEPRINT_ID, AGENT_INSTANCE_ID), BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("agent type"));
        verify(jwkService, never()).getKey(any(), any());
    }

    @Test
    void rejects_whenAgentTypeIsNull() throws Exception {
        stubDiscoveryAndFapi();
        Client blueprint = agentBlueprint(null, ClientAuthenticationMethod.PRIVATE_KEY_JWT, true);
        when(clientLookupService.findByClientId(BLUEPRINT_ID)).thenReturn(Maybe.just(blueprint));

        validator.validate(workloadJwt(BLUEPRINT_ID, AGENT_INSTANCE_ID), BASE_PATH, null).test()
                .assertError(InvalidClientException.class);
    }

    @Test
    void rejects_whenNotAgentApplication() throws Exception {
        stubDiscoveryAndFapi();
        Client blueprint = new Client();
        blueprint.setClientId(BLUEPRINT_ID);
        blueprint.setAppType(ApplicationType.WEB);
        blueprint.setTokenEndpointAuthMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT);
        when(clientLookupService.findByClientId(BLUEPRINT_ID)).thenReturn(Maybe.just(blueprint));

        validator.validate(workloadJwt(BLUEPRINT_ID, AGENT_INSTANCE_ID), BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("not an agent application"));
    }

    @Test
    void rejects_blueprintWithUnsupportedAuthMethod() throws Exception {
        stubDiscoveryAndFapi();
        Client blueprint = agentBlueprint(AgentType.AUTONOMOUS, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, true);
        when(clientLookupService.findByClientId(BLUEPRINT_ID)).thenReturn(Maybe.just(blueprint));

        validator.validate(workloadJwt(BLUEPRINT_ID, AGENT_INSTANCE_ID), BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("not configured for jwt-bearer"));
    }

    @Test
    void rejects_blueprintWithoutJwks() throws Exception {
        stubDiscoveryAndFapi();
        Client blueprint = agentBlueprint(AgentType.AUTONOMOUS, ClientAuthenticationMethod.PRIVATE_KEY_JWT, false);
        when(clientLookupService.findByClientId(BLUEPRINT_ID)).thenReturn(Maybe.just(blueprint));

        validator.validate(workloadJwt(BLUEPRINT_ID, AGENT_INSTANCE_ID), BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("No agent JWKS"));
    }

    @Test
    void rejects_whenBlueprintNotFound() throws Exception {
        stubDiscoveryAndFapi();
        when(clientLookupService.findByClientId(BLUEPRINT_ID)).thenReturn(Maybe.empty());

        validator.validate(workloadJwt(BLUEPRINT_ID, AGENT_INSTANCE_ID), BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("Unknown blueprint"));
    }

    @Test
    void rejects_expiredAssertion() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                new JWTClaimsSet.Builder()
                        .issuer(BLUEPRINT_ID)
                        .subject(AGENT_INSTANCE_ID)
                        .audience(AUDIENCE)
                        .expirationTime(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                        .build());
        jwt.sign(new RSASSASigner(privateKey));

        validator.validate(jwt.serialize(), BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("expired"));
    }

    @Test
    void rejects_assertionMissingRequiredClaims() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                new JWTClaimsSet.Builder().issuer(BLUEPRINT_ID).build());
        jwt.sign(new RSASSASigner(privateKey));

        validator.validate(jwt.serialize(), BASE_PATH, null).test()
                .assertError(InvalidClientException.class);
    }

    @Test
    void rejects_assertionWithWrongAudience() throws Exception {
        stubDiscoveryAndFapi();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                new JWTClaimsSet.Builder()
                        .issuer(BLUEPRINT_ID)
                        .subject(AGENT_INSTANCE_ID)
                        .audience("https://other.example/token")
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                        .build());
        jwt.sign(new RSASSASigner(privateKey));

        validator.validate(jwt.serialize(), BASE_PATH, null).test()
                .assertError(InvalidClientException.class);
        verify(clientLookupService, never()).findByClientId(any());
    }

    @Test
    void rejects_invalidSignature() throws Exception {
        Client blueprint = agentBlueprint(AgentType.AUTONOMOUS, ClientAuthenticationMethod.PRIVATE_KEY_JWT, true);
        stubLookupAndJwk(blueprint, false);

        validator.validate(workloadJwt(BLUEPRINT_ID, AGENT_INSTANCE_ID), BASE_PATH, null).test()
                .assertError(InvalidClientException.class);
    }

    @Test
    void rejects_whenKidHasNoMatchingKey() throws Exception {
        stubDiscoveryAndFapi();
        Client blueprint = agentBlueprint(AgentType.AUTONOMOUS, ClientAuthenticationMethod.PRIVATE_KEY_JWT, true);
        when(clientLookupService.findByClientId(BLUEPRINT_ID)).thenReturn(Maybe.just(blueprint));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.empty());

        validator.validate(workloadJwt(BLUEPRINT_ID, AGENT_INSTANCE_ID), BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("No matching key"));
    }

    // --- helpers -----------------------------------------------------------

    private Client agentBlueprint(AgentType agentType, String authMethod, boolean withJwks) {
        Client blueprint = new Client();
        blueprint.setClientId(BLUEPRINT_ID);
        blueprint.setAppType(ApplicationType.AGENT);
        blueprint.setAgentType(agentType);
        blueprint.setTokenEndpointAuthMethod(authMethod);
        if (withJwks) {
            JWKSet jwks = new JWKSet();
            jwks.setKeys(List.of(jwk));
            blueprint.setJwks(jwks);
        }
        return blueprint;
    }

    private void stubLookupAndJwk(Client blueprint, boolean validSignature) {
        stubDiscoveryAndFapi();
        when(clientLookupService.findByClientId(BLUEPRINT_ID)).thenReturn(Maybe.just(blueprint));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(validSignature);
    }

    private String workloadJwt(String issuer, String subject) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .subject(subject)
                        .audience(AUDIENCE)
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                        .build());
        jwt.sign(new RSASSASigner(privateKey));
        return jwt.serialize();
    }
}
