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
package io.gravitee.am.gateway.handler.ciba.resources.handler;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oidc.AcrValues;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.ciba.CIBAProvider;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequest;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.RoutingContext;
import net.minidev.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationRequestParametersHandlerTest  extends RxWebTestBase {

    public static final String KID = "tu-gio-am";
    @Mock
    private Domain domain;
    @Mock
    private JWSService jwsService;
    @Mock
    private JWKService jwkService;
    @Mock
    private UserService userService;
    @Mock
    private ScopeManager scopeManager;
    @Mock
    private SubjectManager subjectManager;
    private OpenIDProviderMetadata openIDProviderMetadata;

    private Client client;

    private AuthenticationRequestParametersHandlerMock handlerUnderTest;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        when(domain.getOidc()).thenReturn(OIDCSettings.defaultSettings());

        handlerUnderTest = new AuthenticationRequestParametersHandlerMock(domain, jwsService, jwkService, userService, scopeManager, subjectManager);
        router.route(HttpMethod.POST, "/oidc/ciba/authenticate")
                .handler(handlerUnderTest)
                .handler(rc -> rc.response().end())
                .failureHandler(rc -> rc.response().setStatusCode(400).end());

        this.openIDProviderMetadata = new OpenIDProviderMetadata();
        this.openIDProviderMetadata.setIssuer("https://op");
        this.openIDProviderMetadata.setAcrValuesSupported(AcrValues.values());

        this.client = new Client();
        this.client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.CIBA_GRANT_TYPE));
        this.client.setClientId("client_id_iss");
        final ApplicationScopeSettings scope = new ApplicationScopeSettings();
        scope.setScope("openid");
        this.client.setScopeSettings(List.of(scope));

    }

    @Test
    public void shouldRejectRequest_NoScope() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("username");

        handlerUnderTest.setCibaRequest(cibaRequest);

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_MissingOpenIdScope() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("username");
        cibaRequest.setScopes(Set.of("profile", "roles"));

        handlerUnderTest.setCibaRequest(cibaRequest);

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_InvalidAcrValue() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("username");
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(Arrays.asList("urn:mace:incommon:iap:bronze", "urn:mace:incommon:iap:unknown"));
        handlerUnderTest.setCibaRequest(cibaRequest);

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_MissingHints() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(Arrays.asList("urn:mace:incommon:iap:bronze"));

        handlerUnderTest.setCibaRequest(cibaRequest);

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_MultipleHints() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHintToken("loginhinttoken");
        cibaRequest.setLoginHint("username");
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(Arrays.asList("urn:mace:incommon:iap:bronze"));

        handlerUnderTest.setCibaRequest(cibaRequest);

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_MessageTooLong() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("username");
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(Arrays.asList("urn:mace:incommon:iap:bronze"));
        cibaRequest.setBindingMessage("12345678901234567890");

        handlerUnderTest.setCibaRequest(cibaRequest);

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_UserCodeMissing() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("username");
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(Arrays.asList("urn:mace:incommon:iap:bronze"));
        cibaRequest.setBindingMessage("msg");

        client.setBackchannelUserCodeParameter(true);

        handlerUnderTest.setCibaRequest(cibaRequest);

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldAcceptRequest_LoginHint() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("username");
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(Arrays.asList("urn:mace:incommon:iap:bronze"));
        cibaRequest.setBindingMessage("msg");

        client.setBackchannelUserCodeParameter(false);

        handlerUnderTest.setCibaRequest(cibaRequest);

        final User user = new User();
        user.setId(UUID.randomUUID().toString());
        when(userService.findByDomainAndCriteria(any(), any())).thenReturn(Single.just(List.of(user)));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldRejectRequest_TooManyUsers_LoginHint() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("username");
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(Arrays.asList("urn:mace:incommon:iap:bronze"));
        cibaRequest.setBindingMessage("msg");

        client.setBackchannelUserCodeParameter(false);

        handlerUnderTest.setCibaRequest(cibaRequest);

        final User user = new User();
        user.setId(UUID.randomUUID().toString());
        when(userService.findByDomainAndCriteria(any(), any())).thenReturn(Single.just(List.of(user, user)));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_TooManyUsers_LoginTokenHint() throws Exception {
        final JSONObject jwtBody = new JSONObject();
        final JSONObject subId = new JSONObject();
        subId.put("format", "email");
        subId.put("email", "user@email.com");
        jwtBody.put("sub_id", subId);
        JwtHintBuilder hint = new JwtHintBuilder(jwtBody);

        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHintToken(hint.generateHint());
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(Arrays.asList("urn:mace:incommon:iap:bronze"));
        cibaRequest.setBindingMessage("msg");

        client.setBackchannelUserCodeParameter(false);

        handlerUnderTest.setCibaRequest(cibaRequest);

        final io.gravitee.am.model.jose.RSAKey jwk = new io.gravitee.am.model.jose.RSAKey();
        jwk.setKid(KID);
        final JWKSet jwks = new JWKSet();
        jwks.setKeys(List.of(jwk));

        when(jwkService.getKeys(any(Client.class))).thenReturn(Maybe.just(jwks));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        final User user = new User();
        user.setId(UUID.randomUUID().toString());
        when(userService.findByDomainAndCriteria(any(), any())).thenReturn(Single.just(List.of(user, user)));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_LoginTokenHint_Expired() throws Exception {
        final JSONObject jwtBody = new JSONObject();
        final JSONObject subId = new JSONObject();
        subId.put("format", "email");
        subId.put("email", "user@email.com");
        jwtBody.put("sub_id", subId);
        jwtBody.put(Claims.EXP, Instant.now().minusSeconds(10).getEpochSecond());
        JwtHintBuilder hint = new JwtHintBuilder(jwtBody);

        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHintToken(hint.generateHint());
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(Arrays.asList("urn:mace:incommon:iap:bronze"));
        cibaRequest.setBindingMessage("msg");

        client.setBackchannelUserCodeParameter(false);

        handlerUnderTest.setCibaRequest(cibaRequest);

        final io.gravitee.am.model.jose.RSAKey jwk = new io.gravitee.am.model.jose.RSAKey();
        jwk.setKid(KID);
        final JWKSet jwks = new JWKSet();
        jwks.setKeys(List.of(jwk));

        when(jwkService.getKeys(any(Client.class))).thenReturn(Maybe.just(jwks));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldAcceptRequest_LoginTokenHint() throws Exception {
        final JSONObject jwtBody = new JSONObject();
        final JSONObject subId = new JSONObject();
        subId.put("format", "email");
        subId.put("email", "user@email.com");
        jwtBody.put("sub_id", subId);
        JwtHintBuilder hint = new JwtHintBuilder(jwtBody);

        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHintToken(hint.generateHint());
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(Arrays.asList("urn:mace:incommon:iap:bronze"));
        cibaRequest.setBindingMessage("msg");

        client.setBackchannelUserCodeParameter(false);

        handlerUnderTest.setCibaRequest(cibaRequest);

        final io.gravitee.am.model.jose.RSAKey jwk = new io.gravitee.am.model.jose.RSAKey();
        jwk.setKid(KID);
        final JWKSet jwks = new JWKSet();
        jwks.setKeys(List.of(jwk));

        when(jwkService.getKeys(any(Client.class))).thenReturn(Maybe.just(jwks));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        final User user = new User();
        user.setId(UUID.randomUUID().toString());
        when(userService.findByDomainAndCriteria(any(), any())).thenReturn(Single.just(List.of(user)));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldAcceptRequest_IdTokenHint() throws Exception {
        final JSONObject jwtBody = new JSONObject();
        jwtBody.put("sub", UUID.randomUUID().toString());
        jwtBody.put(Claims.EXP, Instant.now().plusSeconds(10).getEpochSecond());
        JwtHintBuilder hint = new JwtHintBuilder(jwtBody);

        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setIdTokenHint(hint.generateHint());
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(Arrays.asList("urn:mace:incommon:iap:bronze"));
        cibaRequest.setBindingMessage("msg");

        client.setBackchannelUserCodeParameter(false);

        handlerUnderTest.setCibaRequest(cibaRequest);

        final io.gravitee.am.model.jose.RSAKey jwk = new io.gravitee.am.model.jose.RSAKey();
        jwk.setKid(KID);
        final JWKSet jwks = new JWKSet();
        jwks.setKeys(List.of(jwk));

        when(jwkService.getKeys()).thenReturn(Single.just(jwks));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);

        final User user = new User();
        user.setId(UUID.randomUUID().toString());
        when(subjectManager.findUserBySub(any())).thenReturn(Maybe.just(user));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    /**
     * Simple class to allow to simply provide CibaAuthenticationRequest for testing
     */
    private class AuthenticationRequestParametersHandlerMock extends AuthenticationRequestParametersHandler {
        private CibaAuthenticationRequest cibaRequest;

        public AuthenticationRequestParametersHandlerMock(Domain domain, JWSService jwsService, JWKService jwkService, UserService userService, ScopeManager scopeManager, SubjectManager subjectManager) {
            super(domain, jwsService, jwkService, userService, scopeManager, subjectManager);
        }

        @Override
        protected CibaAuthenticationRequest createCibaRequest(RoutingContext context) {
            return this.cibaRequest;
        }

        public CibaAuthenticationRequest getCibaRequest() {
            return cibaRequest;
        }

        public void setCibaRequest(CibaAuthenticationRequest cibaRequest) {
            this.cibaRequest = cibaRequest;
        }
    }

    private class JwtHintBuilder {
        private JSONObject payload;
        private RSAKey rsaJWK;
        private RSAKey rsaPublicJWK;

        public JwtHintBuilder(JSONObject payload) throws Exception {
            this.payload = payload;
            this.rsaJWK = new RSAKeyGenerator(2048).keyID(KID).generate();
            this.rsaPublicJWK = rsaJWK.toPublicJWK();
        }

        public JSONObject getPayload() {
            return payload;
        }

        public void setPayload(JSONObject payload) {
            this.payload = payload;
        }

        public RSAKey getRsaJWK() {
            return rsaJWK;
        }

        public void setRsaJWK(RSAKey rsaJWK) {
            this.rsaJWK = rsaJWK;
        }

        public RSAKey getRsaPublicJWK() {
            return rsaPublicJWK;
        }

        public void setRsaPublicJWK(RSAKey rsaPublicJWK) {
            this.rsaPublicJWK = rsaPublicJWK;
        }

        public String generateHint() throws Exception {
            JWSSigner signer = new RSASSASigner(rsaJWK);
            JWSObject jwsObject = new JWSObject(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJWK.getKeyID()).build(),
                    new Payload(this.payload));

            jwsObject.sign(signer);
            return jwsObject.serialize();
        }
    }
}
