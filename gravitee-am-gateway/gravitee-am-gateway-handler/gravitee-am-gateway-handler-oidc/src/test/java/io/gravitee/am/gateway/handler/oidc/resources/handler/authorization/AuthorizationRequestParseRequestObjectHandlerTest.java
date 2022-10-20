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
package io.gravitee.am.gateway.handler.oidc.resources.handler.authorization;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.IOUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseRequestObjectHandler;
import io.gravitee.am.gateway.handler.oauth2.service.par.PushedAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.reactivex.Single;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.AUTH_FLOW_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.REQUEST_PARAMETERS_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationRequestParseRequestObjectHandlerTest {

    @Mock
    private RequestObjectService roService;

    @Mock
    private PushedAuthorizationRequestService parService;

    @Mock
    private AuthenticationFlowContextService authFlowContextService;

    @Mock
    private Domain domain;

    private AuthorizationRequestParseRequestObjectHandler handler;

    @Before
    public void setUp() throws Exception {
        handler = new AuthorizationRequestParseRequestObjectHandler(roService, domain, parService, authFlowContextService);
    }

    @Test
    public void shouldNoPersistPARValues_AuthContextNotPresent() {
        when(domain.usePlainFapiProfile()).thenReturn(false);

        final RoutingContext context = mock(RoutingContext.class);
        final Client client = new Client();
        client.setRequireParRequest(false);
        when(context.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        final HttpServerRequest request = mock(HttpServerRequest.class);

        when(request.getParam(Parameters.REQUEST)).thenReturn(null);
        when(request.getParam(Parameters.REQUEST_URI)).thenReturn(PushedAuthorizationRequestService.PAR_URN_PREFIX+"somevalue");
        when(request.params()).thenReturn(new MultiMap(HeadersMultiMap.httpHeaders()));
        when(context.request()).thenReturn(request);

        when(parService.readFromURI(any(), any(), any())).thenReturn(Single.just(new PlainJWT(new JWTClaimsSet.Builder().claim("parParam1", "parValue1").build())));

        handler.handle(context);

        verify(context).next();
        verify(authFlowContextService, never()).updateContext(any());
    }

    @Test
    public void shouldPersistPARValues_AuthContextPresent() {
        when(domain.usePlainFapiProfile()).thenReturn(false);

        final RoutingContext context = mock(RoutingContext.class);
        final Client client = new Client();
        client.setRequireParRequest(false);
        when(context.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        final HttpServerRequest request = mock(HttpServerRequest.class);

        when(request.getParam(Parameters.REQUEST)).thenReturn(null);
        when(request.getParam(Parameters.REQUEST_URI)).thenReturn(PushedAuthorizationRequestService.PAR_URN_PREFIX+"somevalue");
        when(request.params()).thenReturn(new MultiMap(HeadersMultiMap.httpHeaders()));
        when(context.request()).thenReturn(request);

        final Session session = mock(Session.class);
        when(context.session()).thenReturn(session);

        when(parService.readFromURI(any(), any(), any())).thenReturn(Single.just(new PlainJWT(new JWTClaimsSet.Builder().claim("parParam1", "parValue1").build())));

        final AuthenticationFlowContext authFlowCtx = new AuthenticationFlowContext();
        authFlowCtx.setData(new HashMap<>());
        authFlowCtx.setTransactionId("trxid");
        authFlowCtx.setVersion(1);
        when(context.get(AUTH_FLOW_CONTEXT_KEY)).thenReturn(authFlowCtx);

        when(authFlowContextService.updateContext(any())).thenReturn(Single.just(authFlowCtx));

        handler.handle(context);

        verify(context).next();
        verify(authFlowContextService).updateContext(argThat(ctx -> {
            var hasParValues = ctx.getData().containsKey(REQUEST_PARAMETERS_KEY);
            var parParams = (Map)ctx.getData().get(REQUEST_PARAMETERS_KEY);
            return hasParValues && "parValue1".equals(parParams.get("parParam1"));
        }));
        verify(session).put(eq(ConstantKeys.AUTH_FLOW_CONTEXT_VERSION_KEY), anyInt());
        verify(context).put(eq(ConstantKeys.AUTH_FLOW_CONTEXT_KEY), any());
        verify(context).put(eq(ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY), any());
    }

    @Test
    public void shouldExistWithoutProcessing() {
        when(domain.usePlainFapiProfile()).thenReturn(false);

        final RoutingContext context = mock(RoutingContext.class);
        final Client client = new Client();
        client.setRequireParRequest(false);
        when(context.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.getParam(Parameters.REQUEST)).thenReturn(null);
        when(request.getParam(Parameters.REQUEST_URI)).thenReturn(null);

        when(context.request()).thenReturn(request);
        handler.handle(context);

        verify(context).next();
    }

    @Test
    public void shouldFailsWithout_Request_FapiMode() {
        when(domain.usePlainFapiProfile()).thenReturn(true);

        final RoutingContext context = mock(RoutingContext.class);
        final Client client = new Client();
        client.setRequireParRequest(false);
        when(context.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.getParam(Parameters.REQUEST)).thenReturn(null);
        when(request.getParam(Parameters.REQUEST_URI)).thenReturn(null);

        when(context.request()).thenReturn(request);
        handler.handle(context);

        verify(context, never()).next();
        verify(context).fail(argThat(e -> e instanceof  InvalidRequestException));
    }

    @Test
    public void shouldFailsWithout_Request_ParRequired() {
        final RoutingContext context = mock(RoutingContext.class);
        final Client client = new Client();
        client.setRequireParRequest(true);
        when(context.get(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.getParam(Parameters.REQUEST_URI)).thenReturn(null);

        when(context.request()).thenReturn(request);
        handler.handle(context);

        verify(context, never()).next();
        verify(context).fail(argThat(e -> e instanceof  InvalidRequestException));
    }

    private RSAKey getRSAKey() throws Exception {
        File file = new File(getClass().getClassLoader().getResource("postman_request_object/request_object.key").toURI());
        FileInputStream fis = new FileInputStream(file);
        DataInputStream dis = new DataInputStream(fis);
        byte[] keyBytes = new byte[(int) file.length()];
        dis.readFully(keyBytes);
        dis.close();

        String content = IOUtils.readFileToString(file, StandardCharsets.UTF_8);
        return (RSAKey) JWK.parseFromPEMEncodedObjects(content);
    }

    @Test
    public void invalid_request_object() throws Exception {
        RSAKey rsaKey = getRSAKey();
        JWSSigner signer = new RSASSASigner(rsaKey);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .issuer("https://c2id.com")
                .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa-signature").build(),
                claimsSet);

        signedJWT.sign(signer);

        String jwt = signedJWT.serialize();
        System.out.println(jwt);
    }

    @Test
    public void invalid_client() throws Exception {
        RSAKey rsaKey = getRSAKey();
        JWSSigner signer = new RSASSASigner(rsaKey);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .issuer("https://c2id.com")
                .claim("client_id", "unknown_client")
                .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                .build();

        System.out.println(new PlainJWT(claimsSet).serialize());
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa-signature").build(),
                claimsSet);

        signedJWT.sign(signer);

        String jwt = signedJWT.serialize();
        System.out.println(jwt);
    }

    @Test
    public void invalid_do_not_override_state_and_nonce() throws Exception {
        RSAKey rsaKey = getRSAKey();
        JWSSigner signer = new RSASSASigner(rsaKey);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .issuer("https://c2id.com")
                .claim("state", "override-state")
                .claim("nonce", "override-nonce")
                .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa-signature").build(),
                claimsSet);

        signedJWT.sign(signer);

        String jwt = signedJWT.serialize();
        System.out.println(jwt);
    }

    @Test
    public void override_max_age() throws Exception {
        RSAKey rsaKey = getRSAKey();
        JWSSigner signer = new RSASSASigner(rsaKey);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .issuer("https://c2id.com")
                .claim("max_age", 360000)
                .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa-signature").build(),
                claimsSet);

        signedJWT.sign(signer);

        String jwt = signedJWT.serialize();
        System.out.println(jwt);
    }

    @Test
    public void override_redirect_uri() throws Exception {
        RSAKey rsaKey = getRSAKey();
        JWSSigner signer = new RSASSASigner(rsaKey);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .issuer("https://c2id.com")
                .claim("redirect_uri", "https://op-test:60001/authz_cb")
                .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa-signature").build(),
                claimsSet);

        signedJWT.sign(signer);

        String jwt = signedJWT.serialize();
        System.out.println(jwt);
    }

    @Test
    public void encrypted_request_object() throws Exception {
        RSAKey rsaKey = getRSAKey();
        JWSSigner signer = new RSASSASigner(rsaKey);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .issuer("https://c2id.com")
                .claim("redirect_uri", "https://op-test:60001/authz_cb")
                .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa-encryption").build(),
                claimsSet);

        signedJWT.sign(signer);

        // Create JWE object with signed JWT as payload
        JWEObject jweObject = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM)
                        .contentType("JWT") // required to indicate nested JWT
                        .build(),
                new Payload(signedJWT));

        // Encrypt with the recipient's public key
        jweObject.encrypt(new RSAEncrypter(rsaKey));

        String jwt = jweObject.serialize();
        System.out.println(jwt);
    }

    @Test
    public void encrypted_override_max_age() throws Exception {
        RSAKey rsaKey = getRSAKey();
        JWSSigner signer = new RSASSASigner(rsaKey);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .issuer("https://c2id.com")
                .claim("max_age", 360000)
                .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa-signature").build(),
                claimsSet);

        signedJWT.sign(signer);

        // Create JWE object with signed JWT as payload
        JWEObject jweObject = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM)
                        .contentType("JWT") // required to indicate nested JWT
                        .build(),
                new Payload(signedJWT));

        // Encrypt with the recipient's public key
        jweObject.encrypt(new RSAEncrypter(rsaKey));

        String jwt = jweObject.serialize();
        System.out.println(jwt);
    }

    @Test
    public void encrypted_override_redirect_uri() throws Exception {
        RSAKey rsaKey = getRSAKey();
        JWSSigner signer = new RSASSASigner(rsaKey);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .issuer("https://c2id.com")
                .claim("redirect_uri", "https://op-test:60001/authz_cb")
                .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa-signature").build(),
                claimsSet);

        signedJWT.sign(signer);

        // Create JWE object with signed JWT as payload
        JWEObject jweObject = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM)
                        .contentType("JWT") // required to indicate nested JWT
                        .build(),
                new Payload(signedJWT));

        // Encrypt with the recipient's public key
        jweObject.encrypt(new RSAEncrypter(rsaKey));

        String jwt = jweObject.serialize();
        System.out.println(jwt);
    }
}
