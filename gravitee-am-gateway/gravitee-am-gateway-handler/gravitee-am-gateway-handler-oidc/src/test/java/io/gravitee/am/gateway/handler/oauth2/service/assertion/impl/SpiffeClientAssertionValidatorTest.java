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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.gateway.handler.oidc.service.jws.impl.JWSServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.trustdomain.TrustDomainKeyService;
import io.gravitee.am.gateway.handler.oidc.service.trustdomain.impl.TrustDomainKeyServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.AgentType;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.application.SpiffeApplicationSettings;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.model.oidc.TrustDomainKind;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.gravitee.am.service.jwk.JWKSetFetcher;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpiffeClientAssertionValidatorTest {

    private static final String TRUST_DOMAIN_NAME = "example.org";
    private static final String SUBJECT = "spiffe://example.org/workload/foo";
    private static final String TOKEN_ENDPOINT = "https://am.example.org/oauth/token";
    private static final String BASE_PATH = "/";
    private static final String DOMAIN_ID = "domain-1";
    private static final String KID = "kid-1";

    /** Self-signed throwaway certificate and its private key, used for the PEM key-material path. */
    private static final String PEM_CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIDGzCCAgOgAwIBAgIUTjn3isOFd6UKhH07F2M9E0+1naMwDQYJKoZIhvcNAQEL
            BQAwHDEaMBgGA1UEAwwRdHJ1c3QtZG9tYWluLXRlc3QwIBcNMjYwODE5MDk0NTU3
            WhgPMjEyNjA3MjYwOTQ1NTdaMBwxGjAYBgNVBAMMEXRydXN0LWRvbWFpbi10ZXN0
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1R3nx/2UCXBoJKsq2K3Z
            POGsH8Djxj2nnSZ081MVRQL3YgvrSmgzmRSwr6sYx9oJ8Kn32IINUi/aLiwzxlFL
            ACK3D/vjWmjP+kwIep64LCzHdD7PaP0ePgN/0jme1pOWVQgyHVs+0q0w6vepHpKu
            l9NcAkMPd7Pq0fUTPg9ebW2sGko7EKZvcsE3/nOA7fStycEtmscLr38wZFGIZSIw
            6QJ8Eww9Rn7CFhTXnE88/LGJ104YQ9taDR7uDQdB42dss2YQ/tWF82LL/PS/9zFW
            zdlF11dI1Xyo3LTTt584PQkDXp0B+wrT4YoOskTK2aMO1IIkV/iNMUDs6fzbtJvY
            AQIDAQABo1MwUTAdBgNVHQ4EFgQUmh/tKVGDTOrkfdEJ2J2+ZfA0ufswHwYDVR0j
            BBgwFoAUmh/tKVGDTOrkfdEJ2J2+ZfA0ufswDwYDVR0TAQH/BAUwAwEB/zANBgkq
            hkiG9w0BAQsFAAOCAQEAn84I8ZiCavT+RBk4f0eHd5pnT9Scj0zcEElwgDw+8nNM
            eyTSuPdngL9pQCSBbZbo7ZNCe0WFOf2W/SLIzRx6E3+z1ffAmLr9/LTC1+UNuh1n
            feEljw97Q0sJKd/EyyUg+ZwUwQ5yiTT8hAQdai1kWhRLbalI2cYYJ+1HlXD73eKM
            9WrINYHmLrXclebEtB3SPTTeGtMZ/ajKlcBV1fu/+OoN5e09hvvEEJZ48aNYZrTt
            2An+LQABjo4LNsjP1prK4PtQl1/PI/jHUiRkHfWZx3y2Rakw5bu+bWMMn3vL4Dsk
            rXiZ9rmdTvbQ38TVI3+G5aBLVP9DS+9cXdHfSlyVPg==
            -----END CERTIFICATE-----""";

    private static final String PEM_PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDVHefH/ZQJcGgk
            qyrYrdk84awfwOPGPaedJnTzUxVFAvdiC+tKaDOZFLCvqxjH2gnwqffYgg1SL9ou
            LDPGUUsAIrcP++NaaM/6TAh6nrgsLMd0Ps9o/R4+A3/SOZ7Wk5ZVCDIdWz7SrTDq
            96kekq6X01wCQw93s+rR9RM+D15tbawaSjsQpm9ywTf+c4Dt9K3JwS2axwuvfzBk
            UYhlIjDpAnwTDD1GfsIWFNecTzz8sYnXThhD21oNHu4NB0HjZ2yzZhD+1YXzYsv8
            9L/3MVbN2UXXV0jVfKjctNO3nzg9CQNenQH7CtPhig6yRMrZow7UgiRX+I0xQOzp
            /Nu0m9gBAgMBAAECggEAARl1slq7P0gE3+xaibvUqy0Jq5ZEQ1sN3zbG4Eeu8rUY
            gp+uHitAc0s5VO0S8SdnHFM8Jv7j9ucUfvivNOUDEb3PTo+0AhAYkC5OCUsbulQ2
            z5sDv4M1+zrrl2TA5+yvmCiB90dnuqpP3mTafgDJHszL19Hxvy8LiJUoY45GJFYr
            ykhvqXQflKYhEwiJGxhZ2pHUp6ikjTiRpsDXpaqN3tvKQzPb+d2pRA2QghtJQR/h
            QzK3mxJXZFoPX32htkCVBG50+slf3bvUjdTZHCYRLgUUG+OG+KY2fhVwvbxQdet+
            1tYwoSXubiC8QAiGeSpJyVH5TmVXxFwmjkgzoCQviwKBgQD9BBIH2oUbOAIrSDN8
            n/vGnq1nrbA/jelUwFY28iyXMizQc8aJN6hI7jWDwfXnnJxa8cVtCMIdSOctxFSM
            z3Pk3w8R2Ew8LSPRQR88XnYCoFdtNwdit16TLp0gZgQTc1RVuLwjYoHUgbbS8dx/
            7tnTOQLQkO4YqhFFT0u+WXucYwKBgQDXoV4tFgmHIedQR7IEbspgJhd29gaCox86
            4EHi5Tc4LkVC/IoXm2qgvsD+ip96KnLuU5D+rgBt/OP9MqkqOYL2SmcKUt2Y5Ikd
            qxlTSpviddlHH1aXnd1QlzN4RwoSg5/2dq85gN2DruZoEeXTq3CaxxCbzroSUhhS
            Zcw3N/wNSwKBgQCICLw5BMA0FjT0WGl2Q/32mDHbYIM2aHJfYtn3yNU4pX16xgSk
            mgwcyhZztMElugpGy16+dMsJO70v2jMrUg6Zq2RKz+SNJifvxfepWZBRcSwQSFns
            NXLf51wLo03lt0hMnnpo+/mZ6/QEDo3jKuRqsGhXAtVA5Y9LmIq82RegyQKBgAm5
            0ZGfCNb7hH9ufG0OwjoL/crz1d9WlDYHn3EofZEfJSicU241KF4PvIy+m+rat0xg
            oI2cM5DCc1YcTt0Esw+A05z3l98pj7Wy3fWPy/Au+Jnoo/M6v5y+9SdbrOyg2zo2
            qDijw1oJLYNMK12cwft5NxqP/HyPD2HIDan/8HJ5AoGADAQU2JIJoGVRaaaqbP7/
            s8RNUEDfLE0GWMMj/+V9le/NMVxWh7vUH9Rn+df92ALML7/kHkPEPF4eB9c94fc8
            6cYhyoF2Vpnih499t/hQij7EDf77tUbuuLMQ2XCDdKbqv0J7fhb4HHqznHxvWDsd
            5k+LpV/w04k7axn9U2praD0=
            -----END PRIVATE KEY-----""";

    @Mock
    private ClientLookupService clientLookupService;
    @Mock
    private JWSService jwsService;
    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;
    @Mock
    private Domain domain;
    @Mock
    private TrustDomainKeyService trustDomainKeyService;
    @Mock
    private TrustDomainRepository trustDomainRepository;
    @Mock
    private OpenIDProviderMetadata discovery;

    private RSAPrivateKey privateKey;
    private SpiffeDomainSettings spiffeSettings;
    private OIDCSettings oidcSettings;
    private SpiffeClientAssertionValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKey = (RSAPrivateKey) pair.getPrivate();

        spiffeSettings = new SpiffeDomainSettings();
        spiffeSettings.setEnabled(true);
        oidcSettings = new OIDCSettings();
        oidcSettings.setWorkloadIdentitySettings(spiffeSettings);

        validator = new SpiffeClientAssertionValidator(
                clientLookupService, jwsService, openIDDiscoveryService, domain,
                trustDomainKeyService, trustDomainRepository);
    }

    /** Stubs required by every path that reaches {@code validateSpiffeAssertion} past the sub check. */
    private void stubDiscovery() {
        when(openIDDiscoveryService.getConfiguration(BASE_PATH)).thenReturn(discovery);
        when(discovery.getTokenEndpoint()).thenReturn(TOKEN_ENDPOINT);
    }

    /** Stubs required by every path that reaches the settings check. */
    private void stubDomainOidc() {
        when(domain.getOidc()).thenReturn(oidcSettings);
    }

    /** Stubs required by every path that reaches the trust-domain repository lookup. */
    private void stubDomainId() {
        when(domain.getId()).thenReturn(DOMAIN_ID);
    }

    @Test
    void rejects_whenSpiffeDisabledOnDomain() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        spiffeSettings.setEnabled(false);
        String assertion = svid().serialize();

        validator.validate(assertion, BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("SPIFFE auth disabled"));
        verify(clientLookupService, never()).findByClientId(anyString());
    }

    @Test
    void rejects_whenDomainHasNoOidcSpiffeSettings() throws Exception {
        stubDiscovery();
        when(domain.getOidc()).thenReturn(null);
        String assertion = svid().serialize();

        validator.validate(assertion, BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("SPIFFE auth disabled"));
    }

    @Test
    void rejects_whenDiscoveryMissing() throws Exception {
        when(openIDDiscoveryService.getConfiguration(BASE_PATH)).thenReturn(null);
        String assertion = svid().serialize();

        validator.validate(assertion, BASE_PATH, null).test()
                .assertError(ServerErrorException.class);
    }

    @Test
    void rejects_whenSubIsNotSpiffeId() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                new JWTClaimsSet.Builder().subject("not-spiffe").build());
        jwt.sign(new RSASSASigner(privateKey));

        validator.validate(jwt.serialize(), BASE_PATH, null).test()
                .assertError(InvalidClientException.class);
    }

    @Test
    void rejects_whenClientNotFound() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        when(clientLookupService.findByClientId(SUBJECT)).thenReturn(Maybe.empty());
        String assertion = svid().serialize();

        validator.validate(assertion, BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("Unknown client"));
    }

    @Test
    void rejects_whenClientNotConfiguredForSpiffe() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        Client client = new Client();
        client.setClientId(SUBJECT);
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT);
        when(clientLookupService.findByClientId(SUBJECT)).thenReturn(Maybe.just(client));
        String assertion = svid().serialize();

        validator.validate(assertion, BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("not configured for spiffe_jwt"));
    }

    @Test
    void rejects_whenClientMissingSpiffeSettings() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        Client client = new Client();
        client.setClientId(SUBJECT);
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.SPIFFE_JWT);
        when(clientLookupService.findByClientId(SUBJECT)).thenReturn(Maybe.just(client));
        String assertion = svid().serialize();

        validator.validate(assertion, BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("Client missing SPIFFE settings"));
    }

    @Test
    void rejects_whenTrustDomainNotRegistered() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        stubDomainId();
        Client client = clientWithSpiffeSettings();
        when(clientLookupService.findByClientId(SUBJECT)).thenReturn(Maybe.just(client));
        when(trustDomainRepository.findByName(ReferenceType.DOMAIN, DOMAIN_ID, TrustDomainKind.SPIFFE, TRUST_DOMAIN_NAME))
                .thenReturn(Maybe.empty());
        String assertion = svid().serialize();

        validator.validate(assertion, BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("Trust domain not registered"));
        verify(trustDomainKeyService, never()).getKey(any(), anyString());
    }

    @Test
    void rejects_whenSvidValidatorFails() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        stubDomainId();
        Client client = clientWithSpiffeSettings();
        TrustDomain td = TrustDomain.builder()
                .name(TRUST_DOMAIN_NAME)
                .build();
        when(clientLookupService.findByClientId(SUBJECT)).thenReturn(Maybe.just(client));
        when(trustDomainRepository.findByName(ReferenceType.DOMAIN, DOMAIN_ID, TrustDomainKind.SPIFFE, TRUST_DOMAIN_NAME))
                .thenReturn(Maybe.just(td));
        // aud mismatch — SVID validator returns non-null reason → InvalidClientException
        SignedJWT jwt = svidBuilder()
                .audience("https://other.example/token")
                .build(SUBJECT)
                .signedWith(privateKey);
        String assertion = jwt.serialize();

        validator.validate(assertion, BASE_PATH, null).test()
                .assertError(InvalidClientException.class);
        verify(trustDomainKeyService, never()).getKey(any(), anyString());
    }

    @Test
    void rejects_whenKidMissing() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        stubDomainId();
        Client client = clientWithSpiffeSettings();
        TrustDomain td = TrustDomain.builder().name(TRUST_DOMAIN_NAME).build();
        when(clientLookupService.findByClientId(SUBJECT)).thenReturn(Maybe.just(client));
        when(trustDomainRepository.findByName(ReferenceType.DOMAIN, DOMAIN_ID, TrustDomainKind.SPIFFE, TRUST_DOMAIN_NAME))
                .thenReturn(Maybe.just(td));
        SignedJWT noKid = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                validClaims().build());
        noKid.sign(new RSASSASigner(privateKey));

        validator.validate(noKid.serialize(), BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("SVID missing kid"));
    }

    @Test
    void rejects_whenBundleHasNoMatchingKey() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        stubDomainId();
        Client client = clientWithSpiffeSettings();
        TrustDomain td = TrustDomain.builder().name(TRUST_DOMAIN_NAME).build();
        when(clientLookupService.findByClientId(SUBJECT)).thenReturn(Maybe.just(client));
        when(trustDomainRepository.findByName(ReferenceType.DOMAIN, DOMAIN_ID, TrustDomainKind.SPIFFE, TRUST_DOMAIN_NAME))
                .thenReturn(Maybe.just(td));
        when(trustDomainKeyService.getKey(eq(td), eq(KID))).thenReturn(Maybe.empty());

        validator.validate(svid().serialize(), BASE_PATH, null).test()
                .assertError(err -> err instanceof InvalidClientException
                        && err.getMessage().contains("No matching key"));
    }

    @Test
    void rejects_whenSignatureInvalid() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        stubDomainId();
        Client client = clientWithSpiffeSettings();
        TrustDomain td = TrustDomain.builder().name(TRUST_DOMAIN_NAME).build();
        RSAKey jwk = new RSAKey();
        jwk.setKid(KID);
        when(clientLookupService.findByClientId(SUBJECT)).thenReturn(Maybe.just(client));
        when(trustDomainRepository.findByName(ReferenceType.DOMAIN, DOMAIN_ID, TrustDomainKind.SPIFFE, TRUST_DOMAIN_NAME))
                .thenReturn(Maybe.just(td));
        when(trustDomainKeyService.getKey(eq(td), eq(KID))).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(false);

        validator.validate(svid().serialize(), BASE_PATH, null).test()
                .assertError(InvalidClientException.class);
    }

    @Test
    void resolvesClient_viaClientIdHint_whenProvided() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        stubDomainId();
        // sub is the SPIFFE URI but the form-level client_id should win as lookup id.
        Client client = clientWithSpiffeSettings();
        client.setClientId("hint-client-id");
        TrustDomain td = TrustDomain.builder().name(TRUST_DOMAIN_NAME).build();
        RSAKey jwk = new RSAKey();
        jwk.setKid(KID);

        when(clientLookupService.findByClientId("hint-client-id")).thenReturn(Maybe.just(client));
        when(trustDomainRepository.findByName(ReferenceType.DOMAIN, DOMAIN_ID, TrustDomainKind.SPIFFE, TRUST_DOMAIN_NAME))
                .thenReturn(Maybe.just(td));
        when(trustDomainKeyService.getKey(eq(td), eq(KID))).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        validator.validate(svid().serialize(), BASE_PATH, "hint-client-id").test()
                .assertNoErrors()
                .assertValue(c -> "hint-client-id".equals(c.getClientId()));
        verify(clientLookupService, never()).findByClientId(SUBJECT);
    }

    @Test
    void succeeds_onWellFormedSvid() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        stubDomainId();
        Client client = clientWithSpiffeSettings();
        TrustDomain td = TrustDomain.builder().name(TRUST_DOMAIN_NAME).build();
        RSAKey jwk = new RSAKey();
        jwk.setKid(KID);
        when(clientLookupService.findByClientId(SUBJECT)).thenReturn(Maybe.just(client));
        when(trustDomainRepository.findByName(ReferenceType.DOMAIN, DOMAIN_ID, TrustDomainKind.SPIFFE, TRUST_DOMAIN_NAME))
                .thenReturn(Maybe.just(td));
        when(trustDomainKeyService.getKey(eq(td), eq(KID))).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        validator.validate(svid().serialize(), BASE_PATH, null).test()
                .assertNoErrors()
                .assertValue(c -> SUBJECT.equals(c.getClientId()));
    }

    @Test
    void synthesises_perInstanceAgentClient_forHostedDelegated_withPrefixSubject() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        stubDomainId();

        String parent = "spiffe://example.org/hotel-agent/";
        String instanceSpiffeId = parent + "instance-a";
        String blueprintClientId = "blueprint-1";

        Client blueprint = new Client();
        blueprint.setClientId(blueprintClientId);
        blueprint.setTokenEndpointAuthMethod(ClientAuthenticationMethod.SPIFFE_JWT);
        blueprint.setAppType(ApplicationType.AGENT);
        blueprint.setAgentType(AgentType.HOSTED_DELEGATED);
        SpiffeApplicationSettings appSettings = new SpiffeApplicationSettings();
        appSettings.setTrustDomain(TRUST_DOMAIN_NAME);
        appSettings.setSubject(parent);
        appSettings.setSubjectMatchMode(SpiffeApplicationSettings.SubjectMatchMode.PREFIX);
        blueprint.setWorkloadIdentitySettings(appSettings);

        TrustDomain td = TrustDomain.builder().name(TRUST_DOMAIN_NAME).build();
        RSAKey jwk = new RSAKey();
        jwk.setKid(KID);
        when(clientLookupService.findByClientId(blueprintClientId)).thenReturn(Maybe.just(blueprint));
        when(trustDomainRepository.findByName(ReferenceType.DOMAIN, DOMAIN_ID, TrustDomainKind.SPIFFE, TRUST_DOMAIN_NAME))
                .thenReturn(Maybe.just(td));
        when(trustDomainKeyService.getKey(eq(td), eq(KID))).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        String assertion = instanceSvid(instanceSpiffeId).serialize();

        validator.validate(assertion, BASE_PATH, blueprintClientId).test()
                .assertNoErrors()
                .assertValue(c -> blueprintClientId.equals(c.getClientId())
                        && instanceSpiffeId.equals(c.getAgentInstanceId()));
    }

    @Test
    void synthesises_perInstanceAgentClient_forAutonomous_withPrefixSubject() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        stubDomainId();

        String parent = "spiffe://example.org/hotel-agent/";
        String instanceSpiffeId = parent + "instance-b";
        String blueprintClientId = "blueprint-2";

        Client blueprint = new Client();
        blueprint.setClientId(blueprintClientId);
        blueprint.setTokenEndpointAuthMethod(ClientAuthenticationMethod.SPIFFE_JWT);
        blueprint.setAppType(ApplicationType.AGENT);
        blueprint.setAgentType(AgentType.AUTONOMOUS);
        SpiffeApplicationSettings appSettings = new SpiffeApplicationSettings();
        appSettings.setTrustDomain(TRUST_DOMAIN_NAME);
        appSettings.setSubject(parent);
        appSettings.setSubjectMatchMode(SpiffeApplicationSettings.SubjectMatchMode.PREFIX);
        blueprint.setWorkloadIdentitySettings(appSettings);

        TrustDomain td = TrustDomain.builder().name(TRUST_DOMAIN_NAME).build();
        RSAKey jwk = new RSAKey();
        jwk.setKid(KID);
        when(clientLookupService.findByClientId(blueprintClientId)).thenReturn(Maybe.just(blueprint));
        when(trustDomainRepository.findByName(ReferenceType.DOMAIN, DOMAIN_ID, TrustDomainKind.SPIFFE, TRUST_DOMAIN_NAME))
                .thenReturn(Maybe.just(td));
        when(trustDomainKeyService.getKey(eq(td), eq(KID))).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        validator.validate(instanceSvid(instanceSpiffeId).serialize(), BASE_PATH, blueprintClientId).test()
                .assertNoErrors()
                .assertValue(c -> instanceSpiffeId.equals(c.getAgentInstanceId()));
    }

    @Test
    void doesNotSynthesise_forNonAgentClient_evenWithMatchingSvid() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        stubDomainId();
        Client client = clientWithSpiffeSettings(); // SERVICE-style, no appType=AGENT
        TrustDomain td = TrustDomain.builder().name(TRUST_DOMAIN_NAME).build();
        RSAKey jwk = new RSAKey();
        jwk.setKid(KID);
        when(clientLookupService.findByClientId(SUBJECT)).thenReturn(Maybe.just(client));
        when(trustDomainRepository.findByName(ReferenceType.DOMAIN, DOMAIN_ID, TrustDomainKind.SPIFFE, TRUST_DOMAIN_NAME))
                .thenReturn(Maybe.just(td));
        when(trustDomainKeyService.getKey(eq(td), eq(KID))).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        validator.validate(svid().serialize(), BASE_PATH, null).test()
                .assertNoErrors()
                .assertValue(c -> c.getAgentInstanceId() == null && SUBJECT.equals(c.getClientId()));
    }

    @Test
    void acceptsSvid_signedByPemKeyMaterial() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        stubDomainId();
        Domain realDomain = new Domain();
        realDomain.setId(DOMAIN_ID);
        realDomain.setOidc(oidcSettings);
        SpiffeClientAssertionValidator pemValidator = new SpiffeClientAssertionValidator(
                clientLookupService, new JWSServiceImpl(), openIDDiscoveryService, domain,
                new TrustDomainKeyServiceImpl(mock(JWKSetFetcher.class), realDomain), trustDomainRepository);

        TrustDomain td = TrustDomain.builder()
                .id("td-pem")
                .name(TRUST_DOMAIN_NAME)
                .kind(TrustDomainKind.SPIFFE)
                .keyMaterial(TrustDomainKeyMaterial.builder()
                        .source(KeyMaterialSource.PEM)
                        .certificate(PEM_CERTIFICATE)
                        .build())
                .build();
        when(clientLookupService.findByClientId(SUBJECT)).thenReturn(Maybe.just(clientWithSpiffeSettings()));
        when(trustDomainRepository.findByName(ReferenceType.DOMAIN, DOMAIN_ID, TrustDomainKind.SPIFFE, TRUST_DOMAIN_NAME))
                .thenReturn(Maybe.just(td));

        String assertion = svidSignedBy(pemPrivateKey()).serialize();

        pemValidator.validate(assertion, BASE_PATH, null).test()
                .assertNoErrors()
                .assertValue(c -> SUBJECT.equals(c.getClientId()));
    }

    @Test
    void rejectsSvid_notSignedByPemKeyMaterial() throws Exception {
        stubDiscovery();
        stubDomainOidc();
        stubDomainId();
        Domain realDomain = new Domain();
        realDomain.setId(DOMAIN_ID);
        realDomain.setOidc(oidcSettings);
        SpiffeClientAssertionValidator pemValidator = new SpiffeClientAssertionValidator(
                clientLookupService, new JWSServiceImpl(), openIDDiscoveryService, domain,
                new TrustDomainKeyServiceImpl(mock(JWKSetFetcher.class), realDomain), trustDomainRepository);

        TrustDomain td = TrustDomain.builder()
                .id("td-pem")
                .name(TRUST_DOMAIN_NAME)
                .kind(TrustDomainKind.SPIFFE)
                .keyMaterial(TrustDomainKeyMaterial.builder()
                        .source(KeyMaterialSource.PEM)
                        .certificate(PEM_CERTIFICATE)
                        .build())
                .build();
        when(clientLookupService.findByClientId(SUBJECT)).thenReturn(Maybe.just(clientWithSpiffeSettings()));
        when(trustDomainRepository.findByName(ReferenceType.DOMAIN, DOMAIN_ID, TrustDomainKind.SPIFFE, TRUST_DOMAIN_NAME))
                .thenReturn(Maybe.just(td));

        // signed by the per-test generated key, which the certificate does not carry
        String assertion = svidSignedBy(privateKey).serialize();

        pemValidator.validate(assertion, BASE_PATH, null).test()
                .assertError(InvalidClientException.class);
    }

    private static RSAPrivateKey pemPrivateKey() throws Exception {
        String base64 = PEM_PRIVATE_KEY
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    private SignedJWT svidSignedBy(RSAPrivateKey key) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(SUBJECT)
                .audience(TOKEN_ENDPOINT)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                claims);
        jwt.sign(new RSASSASigner(key));
        return jwt;
    }

    private SignedJWT instanceSvid(String spiffeId) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(spiffeId)
                .audience(TOKEN_ENDPOINT)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                claims);
        jwt.sign(new RSASSASigner(privateKey));
        return jwt;
    }

    // --- helpers -----------------------------------------------------------

    private Client clientWithSpiffeSettings() {
        Client client = new Client();
        client.setClientId(SUBJECT);
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.SPIFFE_JWT);
        SpiffeApplicationSettings appSettings = new SpiffeApplicationSettings();
        appSettings.setTrustDomain(TRUST_DOMAIN_NAME);
        appSettings.setSubject(SUBJECT);
        client.setWorkloadIdentitySettings(appSettings);
        return client;
    }

    private SignedJWT svid() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                validClaims().build());
        jwt.sign(new RSASSASigner(privateKey));
        return jwt;
    }

    private static JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .subject(SUBJECT)
                .audience(TOKEN_ENDPOINT)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)));
    }

    private SvidBuilder svidBuilder() {
        return new SvidBuilder();
    }

    private static class SvidBuilder {
        private final JWTClaimsSet.Builder claims = validClaims();

        SvidBuilder audience(String aud) {
            claims.audience(aud);
            return this;
        }

        SignedSvid build(String sub) {
            claims.subject(sub);
            return new SignedSvid(claims.build());
        }
    }

    private static class SignedSvid {
        private final JWTClaimsSet claims;

        SignedSvid(JWTClaimsSet claims) {
            this.claims = claims;
        }

        SignedJWT signedWith(RSAPrivateKey privateKey) throws Exception {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                    claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt;
        }
    }
}
