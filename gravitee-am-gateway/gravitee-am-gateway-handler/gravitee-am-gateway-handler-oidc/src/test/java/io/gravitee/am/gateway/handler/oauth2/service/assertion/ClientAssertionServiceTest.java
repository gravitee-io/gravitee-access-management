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
package io.gravitee.am.gateway.handler.oauth2.service.assertion;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.impl.AgentJwtBearerClientAssertionValidator;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.impl.ClientAssertionServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.impl.JwtBearerClientAssertionValidator;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.impl.SpiffeClientAssertionValidator;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.gateway.handler.oidc.service.spiffe.TrustBundleService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.gravitee.am.model.application.AgentType;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class ClientAssertionServiceTest {

    private static final String JWT_BEARER_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    private static final String JWT_SPIFFE_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-spiffe";
    private static final String AGENT_JWT_BEARER_TYPE = "urn:gravitee:params:oauth:client-assertion-type:agent-jwt-bearer";
    private static final String CLIENT_ID = "clientIdentifier";
    private static final String AGENT_INSTANCE_ID = "agent-instance-42";
    private static final String ISSUER = CLIENT_ID;
    private static final String AUDIENCE = "https://gravitee.io/test/oauth/token";
    private static final String KID = "keyIdentifier";

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
    private TrustBundleService trustBundleService;

    @Mock
    private TrustDomainRepository trustDomainRepository;

    @Mock
    private io.gravitee.am.service.AuditService auditService;

    private ClientAssertionService clientAssertionService;

    @BeforeEach
    void setUp() {
        var jwtBearer = new JwtBearerClientAssertionValidator(
                clientLookupService, jwkService, jwsService, openIDDiscoveryService, domain);
        var agentJwtBearer = new AgentJwtBearerClientAssertionValidator(
                clientLookupService, jwkService, jwsService, openIDDiscoveryService, domain);
        var spiffe = new SpiffeClientAssertionValidator(
                clientLookupService, jwsService, openIDDiscoveryService, domain, trustBundleService, trustDomainRepository);
        clientAssertionService = new ClientAssertionServiceImpl(List.of(jwtBearer, agentJwtBearer, spiffe));
        lenient().when(clientLookupService.findByClientId(any())).thenReturn(Maybe.empty());
    }

    @Test
    public void testAssertionTypeNotValid() {
        clientAssertionService.assertClient("",null,null).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testUnsupportedAssertionType() {
        clientAssertionService.assertClient("unsupported",null,null).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testAssertionNotValid() {
        clientAssertionService.assertClient(JWT_BEARER_TYPE,"",null).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testSpiffeAssertionTypeAccepted_invalidAssertionStillRejected() {
        clientAssertionService.assertClient(JWT_SPIFFE_TYPE, "", null).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testSpiffeAssertionType_nonSpiffeSubjectRejected() {
        // A JWT whose sub is not a spiffe:// URI must be rejected when sent via the SPIFFE assertion type.
        String assertion = new PlainJWT(
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject(CLIENT_ID)
                        .audience(AUDIENCE)
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        ).serialize();
        clientAssertionService.assertClient(JWT_SPIFFE_TYPE, assertion, null).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testJwtBearer_spiffeSubjectRejectedWithGuidance() {
        // A SPIFFE JWT-SVID sent on jwt-bearer must be rejected with a clear "use jwt-spiffe" message
        // rather than silently falling through to the generic OAuth lookup path.
        String assertion = new PlainJWT(
                new JWTClaimsSet.Builder()
                        .issuer("spiffe://example.org/workload/foo")
                        .subject("spiffe://example.org/workload/foo")
                        .audience(AUDIENCE)
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        ).serialize();
        clientAssertionService.assertClient(JWT_BEARER_TYPE, assertion, null).test()
                .assertError(throwable -> throwable instanceof InvalidClientException
                        && throwable.getMessage() != null
                        && throwable.getMessage().contains("jwt-spiffe"))
                .assertNotComplete();
    }

    @Test
    public void testWithMissingClaims() {
        String assertion = new PlainJWT(new JWTClaimsSet.Builder().build()).serialize();
        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,null).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testWithExpiredToken() {
        String assertion = new PlainJWT(
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject(CLIENT_ID)
                        .audience(AUDIENCE)
                        .expirationTime(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)))
                        .build()
        ).serialize();
        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,null).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testWithFailingDiscovery() {
        String assertion = new PlainJWT(
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject(CLIENT_ID)
                        .audience(AUDIENCE)
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        ).serialize();

        String basePath="/";

        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(null);

        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .assertError(ServerErrorException.class)
                .assertNotComplete();
    }

    @Test
    public void testWithWrongAudience() {
        String assertion = new PlainJWT(
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject(CLIENT_ID)
                        .audience("wrongAudience")
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        ).serialize();

        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath="/";

        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);

        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testPlainJwt() {
        String assertion = new PlainJWT(
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject(CLIENT_ID)
                        .audience(AUDIENCE)
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        ).serialize();

        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath="/";

        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);

        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testJwtWithoutSignature() throws NoSuchAlgorithmException, JOSEException {
        KeyPair rsaKey = generateRsaKeyPair();

        String assertion = generateJWT((RSAPrivateKey) rsaKey.getPrivate());
        assertion = assertion.substring(0, assertion.lastIndexOf(".") + 1); // remove signature
        String basePath="/";

        clientAssertionService.assertClient(JWT_BEARER_TYPE, assertion, basePath)
                .test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testRsaJwt_withoutKid() throws NoSuchAlgorithmException, JOSEException{
        KeyPair rsaKey = generateRsaKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
        key.setN(Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));

        Client client = generateClient(key);
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath="/";

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject(CLIENT_ID)
                        .audience(AUDIENCE)
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        );
        signedJWT.sign(new RSASSASigner(privateKey));
        String assertion = signedJWT.serialize();

        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);
        when(jwkService.getKey(any(),any())).thenReturn(Maybe.just(key));
        when(jwsService.isValidSignature(any(),any())).thenReturn(true);

        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .assertNoErrors()
                .assertValue(client);
    }

    @Test
    public void testRsaJwt_withNoJwks() throws NoSuchAlgorithmException, JOSEException{
        KeyPair rsaKey = generateRsaKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
        key.setN(Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));

        Client client = new Client();
        client.setClientId(CLIENT_ID);
        String assertion = generateJWT(privateKey);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath="/";

        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);

        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testRsaJwt_withClientJwks() throws NoSuchAlgorithmException, JOSEException{
        KeyPair rsaKey = generateRsaKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
        key.setN(Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));

        Client client = generateClient(key);
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT);
        String assertion = generateJWT(privateKey);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath="/";

        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);
        when(jwkService.getKey(any(),any())).thenReturn(Maybe.just(key));
        when(jwsService.isValidSignature(any(),any())).thenReturn(true);

        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .assertNoErrors()
                .assertValue(client);
    }

    @Test
    public void testRsaJwt_withClientJwks_RS256InvalidForFAPI() throws NoSuchAlgorithmException, JOSEException{
        KeyPair rsaKey = generateRsaKeyPair();

        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        String assertion = generateJWT(privateKey);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath="/";

        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);

        when(domain.usePlainFapiProfile()).thenReturn(true);
        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(InvalidClientException.class);
    }

    @Test
    public void testRsaJwt_withClientJwks_invalidClientAuthMethod() throws NoSuchAlgorithmException, JOSEException{
        KeyPair rsaKey = generateRsaKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
        key.setN(Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));

        Client client = generateClient(key);
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT);
        String assertion = generateJWT(privateKey);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath="/";

        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);

        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testRsaJwt_withClientJwksUri() throws NoSuchAlgorithmException, JOSEException{
        KeyPair rsaKey = generateRsaKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
        key.setN(Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(List.of(key));

        Client client = new Client();
        client.setClientId(CLIENT_ID);
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT);
        client.setJwksUri("http://fake/jwk/uri");
        String assertion = generateJWT(privateKey);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath="/";

        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);
        when(jwkService.getKeys(anyString())).thenReturn(Maybe.just(jwkSet));
        when(jwkService.getKey(any(),any())).thenReturn(Maybe.just(key));
        when(jwsService.isValidSignature(any(),any())).thenReturn(true);

        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .assertNoErrors()
                .assertValue(client);
    }

    @Test
    public void testHmacJwt() throws JOSEException {
        // Generate random 256-bit (32-byte) shared secret
        SecureRandom random = new SecureRandom();
        byte[] sharedSecret = new byte[32];
        random.nextBytes(sharedSecret);

        String clientSecret = new String(sharedSecret, StandardCharsets.UTF_8);

        JWSSigner signer = new MACSigner(clientSecret);

        Client client = new Client();
        client.setClientId(CLIENT_ID);
        client.setClientSecret(new String(sharedSecret));
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT);
        String assertion = generateJWT(signer);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath="/";

        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);

        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .assertNoErrors()
                .assertValue(client);
    }

    @Test
    public void testHmacJwt_RS256InvalidForFapi() throws JOSEException {
        // Generate random 256-bit (32-byte) shared secret
        SecureRandom random = new SecureRandom();
        byte[] sharedSecret = new byte[32];
        random.nextBytes(sharedSecret);

        String clientSecret = new String(sharedSecret, StandardCharsets.UTF_8);

        JWSSigner signer = new MACSigner(clientSecret);

        String assertion = generateJWT(signer);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath="/";

        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);

        when(domain.usePlainFapiProfile()).thenReturn(true);

        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(InvalidClientException.class);
    }

    @Test
    public void testHmacJwt_invalidClientAuthMethod() throws JOSEException {
        // Generate random 256-bit (32-byte) shared secret
        SecureRandom random = new SecureRandom();
        byte[] sharedSecret = new byte[32];
        random.nextBytes(sharedSecret);

        String clientSecret = new String(sharedSecret, StandardCharsets.UTF_8);

        JWSSigner signer = new MACSigner(clientSecret);

        Client client = new Client();
        client.setClientId(CLIENT_ID);
        client.setClientSecret(new String(sharedSecret));
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT);
        String assertion = generateJWT(signer);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath="/";

        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);

        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testHmacJwt_invalidAlgorithm() throws NoSuchAlgorithmException, JOSEException {
        KeyPair rsaKey = generateRsaKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
        key.setN(Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(List.of(key));

        Client client = new Client();
        client.setClientId(CLIENT_ID);
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT);
        String assertion = generateJWT(privateKey);

        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath="/";

        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);

        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testRsaJwt_protectedResource() throws NoSuchAlgorithmException, JOSEException {
        KeyPair rsaKey = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
        key.setN(Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));

        Client client = generateClient(key);
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT);
        String assertion = generateJWT(privateKey);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath="/";

        // Regular client not found, but protected resource found
        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);
        when(jwkService.getKey(any(),any())).thenReturn(Maybe.just(key));
        when(jwsService.isValidSignature(any(),any())).thenReturn(true);

        clientAssertionService.assertClient(JWT_BEARER_TYPE,assertion,basePath).test()
                .assertNoErrors()
                .assertValue(client);
    }

    @Test
    public void testHmacJwt_protectedResource() throws JOSEException {
        // HMAC/client_secret_jwt IS supported for protected resources via switchIfEmpty fallback
        SecureRandom random = new SecureRandom();
        byte[] sharedSecret = new byte[32];
        random.nextBytes(sharedSecret);
        String clientSecret = new String(sharedSecret, StandardCharsets.UTF_8);
        JWSSigner signer = new MACSigner(clientSecret);

        Client client = new Client();
        client.setClientId(CLIENT_ID);
        client.setClientSecret(new String(sharedSecret));
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT);

        String assertion = generateJWT(signer);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath = "/";

        // Regular client not found, but protected resource found
        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);

        clientAssertionService.assertClient(JWT_BEARER_TYPE, assertion, basePath).test()
                .assertNoErrors()
                .assertValue(client);
    }

    @Test
    public void testHmacJwt_neitherClientNorProtectedResourceFound() throws JOSEException {
        // When neither regular client nor protected resource is found, should return error
        SecureRandom random = new SecureRandom();
        byte[] sharedSecret = new byte[32];
        random.nextBytes(sharedSecret);
        String clientSecret = new String(sharedSecret, StandardCharsets.UTF_8);
        JWSSigner signer = new MACSigner(clientSecret);

        String assertion = generateJWT(signer);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath = "/";

        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.empty());
        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);

        clientAssertionService.assertClient(JWT_BEARER_TYPE, assertion, basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testRsaJwt_withEmptyTokenEndpointAuthMethod() throws NoSuchAlgorithmException, JOSEException {
        // Empty string tokenEndpointAuthMethod is NOT treated as null — it's an unsupported auth method
        KeyPair rsaKey = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
        key.setN(Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));

        Client client = generateClient(key);
        client.setTokenEndpointAuthMethod(""); // Empty string — unsupported auth method
        String assertion = generateJWT(privateKey);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath = "/";

        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);

        clientAssertionService.assertClient(JWT_BEARER_TYPE, assertion, basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testHmacJwt_withEmptyTokenEndpointAuthMethod() throws JOSEException {
        // Empty string tokenEndpointAuthMethod is NOT treated as null — it's an unsupported auth method
        SecureRandom random = new SecureRandom();
        byte[] sharedSecret = new byte[32];
        random.nextBytes(sharedSecret);
        String clientSecret = new String(sharedSecret, StandardCharsets.UTF_8);
        JWSSigner signer = new MACSigner(clientSecret);

        Client client = new Client();
        client.setClientId(CLIENT_ID);
        client.setClientSecret(new String(sharedSecret));
        client.setTokenEndpointAuthMethod(""); // Empty string — unsupported auth method
        String assertion = generateJWT(signer);
        OpenIDProviderMetadata openIDProviderMetadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath = "/";

        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(openIDProviderMetadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(openIDProviderMetadata);

        clientAssertionService.assertClient(JWT_BEARER_TYPE, assertion, basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    // ==================== Workload-JWT tests ====================

    @Test
    public void testWorkloadJwt_validAssertion() throws Exception {
        KeyPair rsaKey = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
        key.setN(Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));

        Client blueprint = new Client();
        blueprint.setClientId(CLIENT_ID);
        blueprint.setAppType(io.gravitee.am.model.application.ApplicationType.AGENT);
        blueprint.setAgentType(AgentType.AUTONOMOUS);
        blueprint.setTokenEndpointAuthMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT);
        JWKSet agentJwks = new JWKSet();
        agentJwks.setKeys(List.of(key));
        blueprint.setJwks(agentJwks);

        String assertion = generateWorkloadJWT(privateKey, CLIENT_ID, AGENT_INSTANCE_ID);
        OpenIDProviderMetadata metadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath = "/";

        when(metadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(metadata);
        when(clientLookupService.findByClientId(CLIENT_ID)).thenReturn(Maybe.just(blueprint));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(key));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        clientAssertionService.assertClient(AGENT_JWT_BEARER_TYPE, assertion, basePath).test()
                .assertNoErrors()
                .assertValue(client -> {
                    // clientId stays as blueprint, agentInstanceId carries the instance
                    return CLIENT_ID.equals(client.getClientId())
                            && AGENT_INSTANCE_ID.equals(client.getAgentInstanceId())
                            && client.isAgentApplication();
                });
    }

    @Test
    public void testWorkloadJwt_rejectedWhenBlueprintNotConfiguredForAssertion() throws Exception {
        KeyPair rsaKey = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
        key.setN(Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));

        Client blueprint = new Client();
        blueprint.setClientId(CLIENT_ID);
        blueprint.setAppType(io.gravitee.am.model.application.ApplicationType.AGENT);
        blueprint.setAgentType(AgentType.AUTONOMOUS);
        blueprint.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        JWKSet agentJwks = new JWKSet();
        agentJwks.setKeys(List.of(key));
        blueprint.setJwks(agentJwks);

        String assertion = generateWorkloadJWT(privateKey, CLIENT_ID, AGENT_INSTANCE_ID);
        OpenIDProviderMetadata metadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath = "/";

        when(metadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(metadata);
        when(clientLookupService.findByClientId(CLIENT_ID)).thenReturn(Maybe.just(blueprint));

        clientAssertionService.assertClient(AGENT_JWT_BEARER_TYPE, assertion, basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testWorkloadJwt_invalidSignature() throws Exception {
        KeyPair rsaKey = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) rsaKey.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        RSAKey key = new RSAKey();
        key.setKty("RSA");
        key.setKid(KID);
        key.setE(Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));
        key.setN(Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));

        Client blueprint = new Client();
        blueprint.setClientId(CLIENT_ID);
        blueprint.setAppType(io.gravitee.am.model.application.ApplicationType.AGENT);
        blueprint.setAgentType(AgentType.AUTONOMOUS);
        blueprint.setTokenEndpointAuthMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT);
        JWKSet agentJwks = new JWKSet();
        agentJwks.setKeys(List.of(key));
        blueprint.setJwks(agentJwks);

        String assertion = generateWorkloadJWT(privateKey, CLIENT_ID, AGENT_INSTANCE_ID);
        OpenIDProviderMetadata metadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath = "/";

        when(metadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(metadata);
        when(clientLookupService.findByClientId(CLIENT_ID)).thenReturn(Maybe.just(blueprint));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(key));
        when(jwsService.isValidSignature(any(), any())).thenReturn(false);

        clientAssertionService.assertClient(AGENT_JWT_BEARER_TYPE, assertion, basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testWorkloadJwt_nonBlueprintClient() throws Exception {
        KeyPair rsaKey = generateRsaKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        Client regularClient = new Client();
        regularClient.setClientId(CLIENT_ID);

        String assertion = generateWorkloadJWT(privateKey, CLIENT_ID, AGENT_INSTANCE_ID);
        OpenIDProviderMetadata metadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath = "/";

        when(metadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(metadata);
        when(clientLookupService.findByClientId(CLIENT_ID)).thenReturn(Maybe.just(regularClient));

        clientAssertionService.assertClient(AGENT_JWT_BEARER_TYPE, assertion, basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testWorkloadJwt_expiredAssertion() throws Exception {
        KeyPair rsaKey = generateRsaKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                new JWTClaimsSet.Builder()
                        .issuer(CLIENT_ID)
                        .subject(AGENT_INSTANCE_ID)
                        .audience(AUDIENCE)
                        .expirationTime(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)))
                        .build()
        );
        signedJWT.sign(new RSASSASigner(privateKey));

        clientAssertionService.assertClient(AGENT_JWT_BEARER_TYPE, signedJWT.serialize(), "/").test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testWorkloadJwt_userEmbeddedAgentRejected() throws Exception {
        KeyPair rsaKey = generateRsaKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        Client blueprint = new Client();
        blueprint.setClientId(CLIENT_ID);
        blueprint.setAppType(io.gravitee.am.model.application.ApplicationType.AGENT);
        blueprint.setAgentType(AgentType.USER_EMBEDDED);

        String assertion = generateWorkloadJWT(privateKey, CLIENT_ID, AGENT_INSTANCE_ID);
        OpenIDProviderMetadata metadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath = "/";

        when(metadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(metadata);
        when(clientLookupService.findByClientId(CLIENT_ID)).thenReturn(Maybe.just(blueprint));

        clientAssertionService.assertClient(AGENT_JWT_BEARER_TYPE, assertion, basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    @Test
    public void testWorkloadJwt_RS256InvalidForFAPI() throws Exception {
        KeyPair rsaKey = generateRsaKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        String assertion = generateWorkloadJWT(privateKey, CLIENT_ID, AGENT_INSTANCE_ID);
        OpenIDProviderMetadata metadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath = "/";

        lenient().when(metadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        lenient().when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(metadata);
        when(domain.usePlainFapiProfile()).thenReturn(true);

        clientAssertionService.assertClient(AGENT_JWT_BEARER_TYPE, assertion, basePath).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(InvalidClientException.class);
    }

    @Test
    public void testJwtBearer_strictRfc7523_rejectsIssNotEqualSub() throws Exception {
        // RFC 7523: iss MUST equal sub (== client_id). Agent assertions now use
        // the dedicated agent-jwt-bearer assertion type; jwt-bearer is strict.
        KeyPair rsaKey = generateRsaKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKey.getPrivate();

        String assertion = generateWorkloadJWT(privateKey, "blueprint-id", "agent-instance-1");
        OpenIDProviderMetadata metadata = Mockito.mock(OpenIDProviderMetadata.class);
        String basePath = "/";

        lenient().when(metadata.getTokenEndpoint()).thenReturn(AUDIENCE);
        lenient().when(openIDDiscoveryService.getConfiguration(basePath)).thenReturn(metadata);

        clientAssertionService.assertClient(JWT_BEARER_TYPE, assertion, basePath).test()
                .assertError(InvalidClientException.class)
                .assertNotComplete();
    }

    private String generateWorkloadJWT(RSAPrivateKey privateKey, String issuer, String subject) throws JOSEException {
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .subject(subject)
                        .audience(AUDIENCE)
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        );
        signedJWT.sign(new RSASSASigner(privateKey));
        return signedJWT.serialize();
    }

    private KeyPair generateRsaKeyPair() throws NoSuchAlgorithmException{
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    private String generateJWT(RSAPrivateKey privateKey) throws JOSEException {
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject(CLIENT_ID)
                        .audience(AUDIENCE)
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        );

        signedJWT.sign(new RSASSASigner(privateKey));

        return signedJWT.serialize();
    }

    private String generateJWT(JWSSigner jwsSigner) throws JOSEException {
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KID).build(),
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject(CLIENT_ID)
                        .audience(AUDIENCE)
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                        .build()
        );

        signedJWT.sign(jwsSigner);

        return signedJWT.serialize();
    }

    private Client generateClient(JWK jwk) {
        JWKSet jwks = new JWKSet();
        jwks.setKeys(List.of(jwk));
        Client client = new Client();
        client.setClientId(CLIENT_ID);
        client.setJwks(jwks);
        return client;
    }
}
