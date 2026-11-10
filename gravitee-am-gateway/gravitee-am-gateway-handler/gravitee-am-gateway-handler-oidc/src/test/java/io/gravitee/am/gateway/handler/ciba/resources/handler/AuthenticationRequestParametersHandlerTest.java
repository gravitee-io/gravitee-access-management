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
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierProvider;
import io.gravitee.am.authdevice.notifier.api.model.NotifierCapability;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oidc.AcrValues;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.ciba.CIBAProvider;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequest;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequestResolver;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.manager.authdevice.notifier.AuthenticationDeviceNotifierManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidAuthorizationDetailsException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailTypeManager;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailsValidator;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationAuthorizationDetailTypeSettings;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.model.oidc.CIBASettingNotifier;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationRequestParametersHandlerTest  extends RxWebTestBase {

    public static final String KID = "tu-gio-am";
    private static final String PAYMENT = "payment_initiation";
    private static final String ACCOUNT = "account_information";
    @Mock
    private Domain domain;
    @Mock
    private JWSService jwsService;
    @Mock
    private JWKService jwkService;
    @Mock
    private UserGatewayService userService;
    @Mock
    private ScopeManager scopeManager;
    @Mock
    private SubjectManager subjectManager;
    @Mock
    private ProtectedResourceManager protectedResourceManager;
    @Mock
    private AuthenticationDeviceNotifierManager deviceNotifierManager;
    @Mock
    private AuthenticationDeviceNotifierProvider provider;
    private OpenIDProviderMetadata openIDProviderMetadata;

    private Client client;

    private AuthenticationRequestParametersHandlerMock handlerUnderTest;

    private final AuthorizationDetailTypeManager authorizationDetailTypeManager = mock(AuthorizationDetailTypeManager.class);
    private final AuthorizationDetailsValidator authorizationDetailsValidator =
            new AuthorizationDetailsValidator(authorizationDetailTypeManager, mock(EventManager.class), new Domain("domain-id"));
    private final AtomicReference<CibaAuthenticationRequest> acknowledged = new AtomicReference<>();
    private final AtomicReference<Throwable> rejection = new AtomicReference<>();
    private CibaAuthenticationRequest cibaRequest;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        when(domain.getOidc()).thenReturn(OIDCSettings.defaultSettings());

        handlerUnderTest = new AuthenticationRequestParametersHandlerMock();
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
        when(userService.findByCriteria(any())).thenReturn(Single.just(List.of(user)));

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
        when(userService.findByCriteria(any())).thenReturn(Single.just(List.of(user, user)));

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
        when(userService.findByCriteria(any())).thenReturn(Single.just(List.of(user, user)));

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
        when(userService.findByCriteria(any())).thenReturn(Single.just(List.of(user)));

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

    @Test
    public void shouldStoreUserInContext_LoginHint() throws Exception {
        final User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername("testuser");
        user.setExternalId("ext123");
        user.setEmail("test@example.com");

        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("username");
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(List.of("urn:mace:incommon:iap:bronze"));
        cibaRequest.setBindingMessage("msg");
        cibaRequest.setUser(user); // Set the user in the request as the resolver would do

        client.setBackchannelUserCodeParameter(false);

        handlerUnderTest.setCibaRequest(cibaRequest);

        when(userService.findByCriteria(any())).thenReturn(Single.just(List.of(user)));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        // Add a handler after the main handler to verify user is in context
        router.route().order(1).handler(routingContext -> {
            User contextUser = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
            if (contextUser != null && user.getId().equals(contextUser.getId()) && "ext123".equals(contextUser.getExternalId())) {
                routingContext.response().end("OK"); // Use simple OK response
            } else {
                routingContext.response().setStatusCode(500).end("User context test failed");
            }
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldStoreUserInContext_IdTokenHint() throws Exception {
        final String userId = UUID.randomUUID().toString();
        final JSONObject jwtBody = new JSONObject();
        jwtBody.put("sub", userId);
        jwtBody.put(Claims.EXP, Instant.now().plusSeconds(10).getEpochSecond());
        JwtHintBuilder hint = new JwtHintBuilder(jwtBody);

        final User user = new User();
        user.setId(userId);
        user.setUsername("testuser");
        user.setExternalId("ext456");
        user.setEmail("test2@example.com");

        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setIdTokenHint(hint.generateHint());
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(List.of("urn:mace:incommon:iap:bronze"));
        cibaRequest.setBindingMessage("msg");
        cibaRequest.setUser(user); // Set the user in the request as the resolver would do

        client.setBackchannelUserCodeParameter(false);

        handlerUnderTest.setCibaRequest(cibaRequest);

        final io.gravitee.am.model.jose.RSAKey jwk = new io.gravitee.am.model.jose.RSAKey();
        jwk.setKid(KID);
        final JWKSet jwks = new JWKSet();
        jwks.setKeys(List.of(jwk));

        when(jwkService.getKeys()).thenReturn(Single.just(jwks));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);
        when(subjectManager.findUserBySub(any())).thenReturn(Maybe.just(user));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        // Add a handler after the main handler to verify user is in context
        router.route().order(1).handler(routingContext -> {
            User contextUser = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
            if (contextUser != null && user.getId().equals(contextUser.getId()) && "ext456".equals(contextUser.getExternalId())) {
                routingContext.response().end("OK"); // Use simple OK response
            } else {
                routingContext.response().setStatusCode(500).end("User context test failed");
            }
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldStoreUserInContext_LoginTokenHint() throws Exception {
        final JSONObject jwtBody = new JSONObject();
        final JSONObject subId = new JSONObject();
        subId.put("format", "email");
        subId.put("email", "user@email.com");
        jwtBody.put("sub_id", subId);
        JwtHintBuilder hint = new JwtHintBuilder(jwtBody);

        final User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername("testuser");
        user.setExternalId("ext789");
        user.setEmail("user@email.com");

        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHintToken(hint.generateHint());
        cibaRequest.setScopes(Set.of("openid"));
        cibaRequest.setAcrValues(List.of("urn:mace:incommon:iap:bronze"));
        cibaRequest.setBindingMessage("msg");
        cibaRequest.setUser(user); // Set the user in the request as the resolver would do

        client.setBackchannelUserCodeParameter(false);

        handlerUnderTest.setCibaRequest(cibaRequest);

        final io.gravitee.am.model.jose.RSAKey jwk = new io.gravitee.am.model.jose.RSAKey();
        jwk.setKid(KID);
        final JWKSet jwks = new JWKSet();
        jwks.setKeys(List.of(jwk));

        when(jwkService.getKeys(any(Client.class))).thenReturn(Maybe.just(jwks));
        when(jwkService.getKey(any(), any())).thenReturn(Maybe.just(jwk));
        when(jwsService.isValidSignature(any(), any())).thenReturn(true);
        when(userService.findByCriteria(any())).thenReturn(Single.just(List.of(user)));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        // Add a handler after the main handler to verify user is in context
        router.route().order(1).handler(routingContext -> {
            User contextUser = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
            if (contextUser != null && user.getId().equals(contextUser.getId()) && "ext789".equals(contextUser.getExternalId())) {
                routingContext.response().end("OK"); // Use simple OK response
            } else {
                routingContext.response().setStatusCode(500).end("User context test failed");
            }
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    // ----------------------------------------------------------------
    // authorization_details capability tests
    // ----------------------------------------------------------------

    @Test
    public void authorization_details_enabled_when_active_notifier_declares_it() {
        OIDCSettings oidcSettings = OIDCSettings.defaultSettings();
        CIBASettingNotifier notifier = new CIBASettingNotifier();
        notifier.setId("n1");
        oidcSettings.getCibaSettings().setDeviceNotifiers(List.of(notifier));
        when(domain.getOidc()).thenReturn(oidcSettings);

        AuthenticationRequestParametersHandlerMock h =
                new AuthenticationRequestParametersHandlerMock();

        when(provider.capabilities()).thenReturn(Set.of(NotifierCapability.AUTHORIZATION_DETAILS));
        when(deviceNotifierManager.getAuthDeviceNotifierProvider("n1")).thenReturn(provider);

        assertTrue(h.authorizationDetailsEnabled());
    }

    @Test
    public void authorization_details_disabled_for_notifier_without_capability() {
        OIDCSettings oidcSettings = OIDCSettings.defaultSettings();
        CIBASettingNotifier notifier = new CIBASettingNotifier();
        notifier.setId("n1");
        oidcSettings.getCibaSettings().setDeviceNotifiers(List.of(notifier));
        when(domain.getOidc()).thenReturn(oidcSettings);

        AuthenticationRequestParametersHandlerMock h =
                new AuthenticationRequestParametersHandlerMock();

        when(provider.capabilities()).thenReturn(Set.of());
        when(deviceNotifierManager.getAuthDeviceNotifierProvider("n1")).thenReturn(provider);

        assertFalse(h.authorizationDetailsEnabled());
    }

    // ----------------------------------------------------------------
    // authorization_details checks
    // ----------------------------------------------------------------

    @Test
    public void shouldKeepTheAuthorizationDetailsOfTheFormParameter() throws Exception {
        routeAuthorizationDetails(null);

        sendAuthorizationDetails("[{\"type\":\"payment_initiation\",\"actions\":[\"initiate\"]},{\"type\":\"account_information\"}]",
                HttpStatusCode.OK_200, "OK");

        assertNull(rejection.get());
        assertEquals(List.of(Map.of("type", PAYMENT, "actions", List.of("initiate")), Map.of("type", ACCOUNT)),
                acknowledged.get().getAuthorizationDetails().getRaw());
    }

    @Test
    public void shouldKeepTheAuthorizationDetailsOfTheRequestObject() throws Exception {
        List<Map<String, Object>> claim = List.of(Map.of("type", PAYMENT, "actions", List.of("initiate")));
        routeAuthorizationDetails(requestObject(claim));

        sendAuthorizationDetails(null, HttpStatusCode.OK_200, "OK");

        assertNull(rejection.get());
        assertEquals(claim, acknowledged.get().getAuthorizationDetails().getRaw());
    }

    @Test
    public void shouldAcceptARequestWithoutAuthorizationDetails() throws Exception {
        routeAuthorizationDetails(null);

        sendAuthorizationDetails(null, HttpStatusCode.OK_200, "OK");

        assertNull(rejection.get());
        assertNull(acknowledged.get().getAuthorizationDetails());
    }

    @Test
    public void shouldRejectMalformedAuthorizationDetailsBeforeTheNotifier() throws Exception {
        routeAuthorizationDetails(null);

        sendAuthorizationDetails("not-json", HttpStatusCode.BAD_REQUEST_400, "Bad Request");

        assertRejected(InvalidRequestException.class, "authorization_details is not valid JSON");
    }

    @Test
    public void shouldRejectAnElementWithoutTypeBeforeTheNotifier() throws Exception {
        routeAuthorizationDetails(null);

        sendAuthorizationDetails("[{}]", HttpStatusCode.BAD_REQUEST_400, "Bad Request");

        assertRejected(InvalidRequestException.class, "authorization_details[0] must have a non-empty string type");
    }

    @Test
    public void shouldRejectABlankFormParameter() throws Exception {
        routeAuthorizationDetails(null);

        sendAuthorizationDetails(" ", HttpStatusCode.BAD_REQUEST_400, "Bad Request");

        assertRejected(InvalidRequestException.class, "authorization_details must be a non-empty JSON array");
    }

    @Test
    public void shouldRejectATypeNotEnabledOnTheClientBeforeTheNotifier() throws Exception {
        routeAuthorizationDetails(null);

        sendAuthorizationDetails("[{\"type\":\"internal_transfer\"}]", HttpStatusCode.BAD_REQUEST_400, "Bad Request");

        assertRejected(InvalidAuthorizationDetailsException.class, "authorization_details[0] of type 'internal_transfer' is not allowed for this client");
    }

    @Test
    public void shouldRejectARequestObjectClaimHoldingAString() throws Exception {
        routeAuthorizationDetails(requestObject("[{\"type\":\"payment_initiation\"}]"));

        sendAuthorizationDetails(null, HttpStatusCode.BAD_REQUEST_400, "Bad Request");

        assertRejected(InvalidRequestException.class, "authorization_details must be a non-empty JSON array");
    }

    @Test
    public void shouldRejectARequestObjectClaimWithATypeNotEnabledOnTheClient() throws Exception {
        routeAuthorizationDetails(requestObject(List.of(Map.of("type", "internal_transfer"))));

        sendAuthorizationDetails(null, HttpStatusCode.BAD_REQUEST_400, "Bad Request");

        assertRejected(InvalidAuthorizationDetailsException.class, "authorization_details[0] of type 'internal_transfer' is not allowed for this client");
    }

    @Test
    public void shouldCheckTheScopeBeforeTheAuthorizationDetails() throws Exception {
        routeAuthorizationDetails(null);
        cibaRequest.setScopes(Set.of("profile"));

        sendAuthorizationDetails("not-json", HttpStatusCode.BAD_REQUEST_400, "Bad Request");

        assertRejected(InvalidScopeException.class, "scope is missing or doesn't contain 'openid'");
    }

    @Test
    public void shouldIgnoreAuthorizationDetailsWhenTheNotifierDoesNotSupportThem() throws Exception {
        routeAuthorizationDetails(null, Set.of());

        sendAuthorizationDetails("[{\"type\":\"payment_initiation\"}]", HttpStatusCode.OK_200, "OK");

        assertNull(rejection.get());
        assertNull(acknowledged.get().getAuthorizationDetails());
    }

    @Test
    public void shouldNotValidateAuthorizationDetailsWhenTheNotifierDoesNotSupportThem() throws Exception {
        routeAuthorizationDetails(null, Set.of());

        sendAuthorizationDetails("not-json", HttpStatusCode.OK_200, "OK");

        assertNull(rejection.get());
        assertNull(acknowledged.get().getAuthorizationDetails());
    }

    /**
     * Routes {@code /oidc/ciba/authenticate/rar} to the handler, with a notifier supporting authorization details, for a client
     * enabling {@link #PAYMENT} and {@link #ACCOUNT} on a domain that has every type, then to a stand-in for the notifier that
     * records the request it gets.
     */
    private void routeAuthorizationDetails(SignedJWT requestObject) {
        routeAuthorizationDetails(requestObject, Set.of(NotifierCapability.AUTHORIZATION_DETAILS));
    }

    private void routeAuthorizationDetails(SignedJWT requestObject, Set<NotifierCapability> notifierCapabilities) {
        OIDCSettings oidcSettings = OIDCSettings.defaultSettings();
        CIBASettingNotifier notifier = new CIBASettingNotifier();
        notifier.setId("n1");
        oidcSettings.getCibaSettings().setDeviceNotifiers(List.of(notifier));
        lenient().when(provider.capabilities()).thenReturn(notifierCapabilities);
        lenient().when(deviceNotifierManager.getAuthDeviceNotifierProvider("n1")).thenReturn(provider);
        when(domain.getOidc()).thenReturn(oidcSettings);
        lenient().when(authorizationDetailTypeManager.findByType(anyString())).thenAnswer(invocation ->
                Optional.of(AuthorizationDetailType.builder().id(invocation.getArgument(0) + "-id").type(invocation.getArgument(0)).build()));
        client.setAuthorizationDetailTypes(List.of(new ApplicationAuthorizationDetailTypeSettings(PAYMENT), new ApplicationAuthorizationDetailTypeSettings(ACCOUNT)));
        client.setBackchannelUserCodeParameter(false);

        AuthenticationRequestParametersHandlerMock handler =
                new AuthenticationRequestParametersHandlerMock();
        cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("username");
        cibaRequest.setScopes(Set.of("openid"));
        handler.setCibaRequest(cibaRequest);

        final User user = new User();
        user.setId(UUID.randomUUID().toString());
        lenient().when(userService.findByCriteria(any())).thenReturn(Single.just(List.of(user)));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            if (requestObject != null) {
                routingContext.put(ConstantKeys.REQUEST_OBJECT_KEY, requestObject);
            }
            routingContext.next();
        });
        router.route(HttpMethod.POST, "/oidc/ciba/authenticate/rar")
                .handler(handler)
                .handler(rc -> {
                    acknowledged.set(rc.get(ConstantKeys.CIBA_AUTH_REQUEST_KEY));
                    rc.response().end();
                })
                .failureHandler(rc -> {
                    rejection.set(rc.failure());
                    rc.response().setStatusCode(400).end();
                });
    }

    private void sendAuthorizationDetails(String authorizationDetails, int status, String statusMessage) throws Exception {
        String query = authorizationDetails == null ? "" : "?authorization_details=" + URLEncoder.encode(authorizationDetails, StandardCharsets.UTF_8);
        testRequest(HttpMethod.POST, "/oidc/ciba/authenticate/rar" + query, null, status, statusMessage, null);
    }

    private void assertRejected(Class<? extends Throwable> error, String description) {
        assertNull("the notifier must not be reached", acknowledged.get());
        assertEquals(error, rejection.get().getClass());
        assertEquals(description, rejection.get().getMessage());
    }

    /**
     * @return a parsed request object, whose claim has the type the gateway reads
     */
    private static SignedJWT requestObject(Object authorizationDetails) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().claim("authorization_details", authorizationDetails).build();
        SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signed.sign(new MACSigner("0123456789abcdef0123456789abcdef".getBytes()));
        return SignedJWT.parse(signed.serialize());
    }

    private CibaAuthenticationRequestResolver cibaRequestResolver() {
        CibaAuthenticationRequestResolver resolver = new CibaAuthenticationRequestResolver(domain, jwsService, jwkService, userService, subjectManager, deviceNotifierManager);
        resolver.setManagers(scopeManager, protectedResourceManager);
        return resolver;
    }

    /**
     * Simple class to allow to simply provide CibaAuthenticationRequest for testing
     */
    private class AuthenticationRequestParametersHandlerMock extends AuthenticationRequestParametersHandler {
        private CibaAuthenticationRequest cibaRequest;

        public AuthenticationRequestParametersHandlerMock() {
            super(domain, cibaRequestResolver(), deviceNotifierManager, authorizationDetailsValidator);
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
