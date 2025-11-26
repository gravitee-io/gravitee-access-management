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
package io.gravitee.am.gateway.handler.oauth2.service.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.jwt.CertificateInfo;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.EncodedJWT;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.context.ReactableExecutionContext;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.TokenServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Response;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.gateway.handler.dummies.TestCertificateInfoFactory.createTestCertificateInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class TokenServiceTest {

    @InjectMocks
    private TokenServiceImpl tokenService = new TokenServiceImpl();

    @Mock
    private AccessTokenRepository accessTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenEnhancer tokenEnhancer;

    @Mock
    private JWTService jwtService;

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Mock
    private ExecutionContextFactory executionContextFactory;

    @Mock
    private TokenManager tokenManager;

    @Mock
    private AuditService auditService;

    @Mock
    private SubjectManager subjectManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CertificateInfo signingCertificateInfo = createTestCertificateInfo();

    private ArgumentMatcher<AuditBuilder<?>> auditMatcher = auditBuilder -> auditBuilder instanceof ClientTokenAuditBuilder;

    @AfterEach
    public void after() {
        verify(auditService, times(1)).report(argThat(auditMatcher));
    }

    @Test
    public void shouldCreate_clientOnly() {
        OAuth2Request oAuth2Request = new OAuth2Request();

        Client client = new Client();
        client.setClientId("my-client-id");

        ExecutionContext executionContext = mock(ExecutionContext.class);

        when(jwtService.encodeJwt(any(), any(Client.class))).thenReturn(Single.just(sampleEncodedJwt()));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(new AccessToken("token-id")));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        doReturn(Completable.complete()).when(tokenManager).storeAccessToken(any());
        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenManager, times(1)).storeAccessToken(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());

        expectTokenCreatedAuditLog();
    }

    @Test
    public void shouldCreate_forUser() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setSubject("userid");
        MultiValueMap<String, String> additionalParameters = new LinkedMultiValueMap<>();
        additionalParameters.add("key", "value");
        oAuth2Request.setAdditionalParameters(additionalParameters);

        Client client = new Client();
        client.setClientId("my-client-id");

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(jwtService.encodeJwt(any(), any(Client.class))).thenReturn(Single.just(sampleEncodedJwt()));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenAnswer(ans -> Single.just(ans.getArgument(0)));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        doReturn(Completable.complete()).when(tokenManager).storeAccessToken(any());
        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(token -> token.getAdditionalInformation().containsKey("key"));

        verify(tokenManager, times(1)).storeAccessToken(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());

        expectTokenCreatedAuditLog();
    }

    @Test
    public void shouldCreateWithoutAdditionalParameters() {
        tokenService.setStrictResponse(true);
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setSubject("userid");
        MultiValueMap<String, String> additionalParameters = new LinkedMultiValueMap<>();
        additionalParameters.add("key", "value");
        oAuth2Request.setAdditionalParameters(additionalParameters);

        Client client = new Client();
        client.setClientId("my-client-id");

        ExecutionContext executionContext = mock(ExecutionContext.class);

        when(jwtService.encodeJwt(any(), any(Client.class))).thenReturn(Single.just(sampleEncodedJwt()));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenAnswer(ans -> Single.just(ans.getArgument(0)));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        doReturn(Completable.complete()).when(tokenManager).storeAccessToken(any());
        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        testObserver.assertValue(token -> token.getAdditionalInformation().isEmpty());

        verify(tokenManager, times(1)).storeAccessToken(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());
        verify(subjectManager).updateJWT(any(), any());

        expectTokenCreatedAuditLog();
    }

    @Test
    public void shouldCreateAndSkipAdditionalParameters() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        MultiValueMap<String, String> additionalParameters = new LinkedMultiValueMap<>();
        additionalParameters.add("access_token", "dummy token");
        additionalParameters.add("id_token", "dummy token");
        oAuth2Request.setAdditionalParameters(additionalParameters);

        Client client = new Client();
        client.setClientId("my-client-id");

        ExecutionContext executionContext = mock(ExecutionContext.class);

        when(jwtService.encodeJwt(any(), any(Client.class))).thenReturn(Single.just(sampleEncodedJwt()));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenAnswer(ans -> Single.just(ans.getArgument(0)));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        doReturn(Completable.complete()).when(tokenManager).storeAccessToken(any());
        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        testObserver.assertValue(token -> token.getAdditionalInformation().isEmpty());

        verify(tokenManager, times(1)).storeAccessToken(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());

        expectTokenCreatedAuditLog();
    }

    @Test
    public void shouldCreate_withSafeRequest() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setExecutionContext(Map.of("key", "val"));
        Response mockResponse = mock(Response.class);
        oAuth2Request.setHttpResponse(mockResponse);

        Client client = new Client();
        client.setClientId("my-client-id");

        ExecutionContext executionContext = mock(ExecutionContext.class);

        when(jwtService.encodeJwt(any(), any(Client.class))).thenReturn(Single.just(sampleEncodedJwt()));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(new AccessToken("token-id")));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        doReturn(Completable.complete()).when(tokenManager).storeAccessToken(any());
        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenManager, times(1)).storeAccessToken(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);

        verify(executionContext, times(2)).setAttribute(keyCaptor.capture(), valueCaptor.capture());

        assertEquals("tokenRequest", keyCaptor.getAllValues().get(0));
        Assertions.assertNull(((OAuth2Request) valueCaptor.getAllValues().get(0)).getExecutionContext());
        Assertions.assertNull(((OAuth2Request) valueCaptor.getAllValues().get(0)).getHttpResponse());

        expectTokenCreatedAuditLog();
    }

    @Test
    public void shouldCreateAccessTokenWithRefreshToken() {
        OAuth2Request oAuth2Request = new OAuth2Request();

        Client client = new Client();
        client.setClientId("my-client-id");

        AccessToken accessToken = new AccessToken("token-value");
        accessToken.setRefreshToken("refresh-token-value");

        ExecutionContext executionContext = mock(ExecutionContext.class);

        when(jwtService.encodeJwt(any(), any(Client.class))).thenReturn(Single.just(sampleEncodedJwt()));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(accessToken));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        doReturn(Completable.complete()).when(tokenManager).storeAccessToken(any());
        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenManager, times(1)).storeAccessToken(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());

        expectTokenCreatedAuditLog();
    }

    @Test
    public void shouldCreate_withAudArray_forceClientId() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("my-client-id");

        Client client = new Client();
        client.setClientId("my-client-id");
        TokenClaim customClaim = new TokenClaim();
        customClaim.setTokenType(TokenTypeHint.ACCESS_TOKEN);
        customClaim.setClaimName(Claims.AUD);
        String customClaimExpression = "{T(java.lang.String).valueOf(\"another_client_identifier,other_value\").split(\",\")}";
        customClaim.setClaimValue(customClaimExpression);
        client.setTokenCustomClaims(List.of(customClaim));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        TemplateEngine tplEngine = mock(TemplateEngine.class);
        when(tplEngine.getValue(eq(customClaimExpression), any())).thenReturn(new String[]{"another_client_identifier", "other_value"});
        when(executionContext.getTemplateEngine()).thenReturn(tplEngine);

        when(jwtService.encodeJwt(any(), any(Client.class))).thenReturn(Single.just(sampleEncodedJwt()));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(new AccessToken("token-id")));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        doReturn(Completable.complete()).when(tokenManager).storeAccessToken(any());
        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenManager, times(1)).storeAccessToken(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());
        verify(jwtService).encodeJwt(argThat(jwt -> jwt.get(Claims.AUD) instanceof List
                && jwt.getAud().equals(((List) jwt.get(Claims.AUD)).get(0))
                && ((List) jwt.get(Claims.AUD)).size() == 3
                && oAuth2Request.getClientId().equals(jwt.getAud())), any(Client.class));

        expectTokenCreatedAuditLog();
    }

    @Test
    public void shouldNotCreate_StoreTokenFails() {
        OAuth2Request oAuth2Request = new OAuth2Request();

        Client client = new Client();
        client.setClientId("my-client-id");

        ExecutionContext executionContext = mock(ExecutionContext.class);

        when(jwtService.encodeJwt(any(), any(Client.class))).thenReturn(Single.just(sampleEncodedJwt()));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(new AccessToken("token-id")));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        doReturn(Completable.error(new TechnicalException())).when(tokenManager).storeAccessToken(any());

        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(TechnicalException.class);

        verify(tokenManager, times(1)).storeAccessToken(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());

        expectTokenCreatedFailedAuditLog();
    }

    @Test
    public void shouldCreateWithPermissions() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setPermissions(Collections.singletonList(new PermissionRequest().setResourceId("rs_one")));

        Client client = new Client();
        client.setClientId("my-client-id");

        ExecutionContext executionContext = mock(ExecutionContext.class);

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        when(jwtService.encodeJwt(jwtCaptor.capture(), any(Client.class))).thenReturn(Single.just(sampleEncodedJwt()));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(new AccessToken("token-id")));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        doReturn(Completable.complete()).when(tokenManager).storeAccessToken(any());
        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        JWT jwt = jwtCaptor.getValue();
        assertTrue(jwt != null && jwt.get("permissions") != null);
        verify(tokenManager, times(1)).storeAccessToken(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());

        expectTokenCreatedAuditLog();
    }

    @Test
    public void shouldCreateWithCustomClaims() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.getContext().put(ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY, new HashMap<>());

        TokenClaim customClaim = new TokenClaim();
        customClaim.setTokenType(TokenTypeHint.ACCESS_TOKEN);
        customClaim.setClaimName("iss");
        customClaim.setClaimValue("https://custom-iss");

        TokenClaim customClaim2 = new TokenClaim();
        customClaim2.setTokenType(TokenTypeHint.ACCESS_TOKEN);
        customClaim2.setClaimName("aud");
        customClaim2.setClaimValue("my-api");

        Client client = new Client();
        client.setClientId("my-client-id");
        client.setTokenCustomClaims(Arrays.asList(customClaim, customClaim2));

        ReactableExecutionContext executionContext = mock(ReactableExecutionContext.class);
        TemplateEngine templateEngine = mock(TemplateEngine.class);
        when(templateEngine.getValue("https://custom-iss", Object.class)).thenReturn("https://custom-iss");
        when(templateEngine.getValue("my-api", Object.class)).thenReturn("my-api");
        when(executionContext.getTemplateEngine()).thenReturn(templateEngine);

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        when(jwtService.encodeJwt(jwtCaptor.capture(), any(Client.class))).thenReturn(Single.just(sampleEncodedJwt()));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(new AccessToken("token-id")));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        doReturn(Completable.complete()).when(tokenManager).storeAccessToken(any());

        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        JWT jwt = jwtCaptor.getValue();
        assertNotNull(jwt);
        assertTrue(jwt.get("iss") != null && "https://custom-iss".equals(jwt.get("iss")));
        assertTrue(jwt.get("aud") != null && "my-api".equals(jwt.get("aud")));
        verify(tokenManager, times(1)).storeAccessToken(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());
        verify(executionContext).setAttribute(eq(ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY), any());

        expectTokenCreatedAuditLog();
    }

    @Test
    public void shouldRefresh() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId(clientId);

        Client client = new Client();
        client.setId(clientId);
        client.setClientId(clientId);

        String token = "refresh-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(token);
        refreshToken.setToken(token);
        refreshToken.setSubject("subject");
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 10000));

        JWT jwt = new JWT();
        jwt.setJti(token);
        jwt.setAud(clientId);
        jwt.setExp(refreshToken.getExpireAt().getTime() / 1000L);

        when(jwtService.decodeAndVerify(any(), any(Client.class), any())).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));
        when(refreshTokenRepository.delete(anyString())).thenReturn(Completable.complete());

        TestObserver<Token> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest, client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, times(1)).delete(anyString());

        expectTokenRefreshAuditLog();
    }

    @Test
    public void shouldRefreshWithPermissions() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId(clientId);

        Client client = new Client();
        client.setId(clientId);
        client.setClientId(clientId);

        String token = "refresh-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(token);
        refreshToken.setToken(token);
        refreshToken.setSubject("subject");
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 10000));

        JWT jwt = new JWT();
        jwt.setJti(token);
        jwt.setAud(clientId);
        jwt.setExp(refreshToken.getExpireAt().getTime() / 1000L);
        jwt.put("permissions", Collections.singletonList(new PermissionRequest().setResourceId("one").setResourceScopes(List.of("A"))));

        when(jwtService.decodeAndVerify(any(), any(Client.class), any())).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));
        when(refreshTokenRepository.delete(anyString())).thenReturn(Completable.complete());

        TestObserver<Token> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest, client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        //Check permissions are well available into the refresh_token object.
        testObserver.assertValue(token1 -> token1.getAdditionalInformation().get("permissions") != null);
        //Check TokenRequest permissions field is well filled (will be used to propagate the permission into the final access_token)
        List<PermissionRequest> permissions = tokenRequest.getPermissions();
        assertNotNull(permissions);
        assertTrue("one".equals(permissions.get(0).getResourceId()) && "A".equals(permissions.get(0).getResourceScopes().get(0)));
        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, times(1)).delete(anyString());

        expectTokenRefreshAuditLog();
    }

    @Test
    public void shouldNotRefresh_refreshNotFound() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId(clientId);

        String token = "refresh-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(token);
        refreshToken.setToken(token);
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 10000));

        Client client = new Client();
        client.setClientId(clientId);

        JWT jwt = new JWT();
        jwt.setJti(token);
        jwt.setAud(clientId);
        jwt.setExp(refreshToken.getExpireAt().getTime() / 1000L);

        when(jwtService.decodeAndVerify(eq("encoded"), any(Client.class), any())).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.empty());

        TestObserver<Token> testObserver = tokenService.refresh("encoded", tokenRequest, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, never()).delete(anyString());
        verify(accessTokenRepository, never()).create(any());

        expectTokenRefreshFailedAuditLog();
    }

    @Test
    public void shouldNotRefresh_refreshExpired() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId(clientId);

        String token = "refresh-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(token);
        refreshToken.setToken(token);
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() - 10000));

        Client client = new Client();
        client.setClientId(clientId);

        JWT jwt = new JWT();
        jwt.setJti(token);
        jwt.setAud(clientId);
        jwt.setExp(refreshToken.getExpireAt().getTime() / 1000L);

        when(jwtService.decodeAndVerify(eq(refreshToken.getToken()), any(Client.class), any())).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));

        TestObserver<Token> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, never()).delete(anyString());
        verify(accessTokenRepository, never()).create(any());

        expectTokenRefreshFailedAuditLog();
    }

    @Test
    public void shouldNotRefresh_notTheSameClient() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("wrong-client-id");

        String token = "refresh-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(token);
        refreshToken.setToken(token);
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 10000));

        Client client = new Client();
        client.setClientId(clientId);

        JWT jwt = new JWT();
        jwt.setJti(token);
        jwt.setAud(clientId);
        jwt.setExp(refreshToken.getExpireAt().getTime() / 1000L);

        when(jwtService.decodeAndVerify(any(), any(Client.class), any())).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));

        TestObserver<Token> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, never()).delete(anyString());
        verify(accessTokenRepository, never()).create(any());

        expectTokenRefreshFailedAuditLog();
    }

    @Test
    public void shouldNotRefresh_invalidTokenException() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId(clientId);

        Client client = new Client();
        client.setClientId(clientId);

        when(jwtService.decodeAndVerify(eq("encoded"), any(Client.class), any())).thenReturn(Single.error(new InvalidTokenException()));

        TestObserver<Token> testObserver = tokenService.refresh("encoded", tokenRequest, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(refreshTokenRepository, never()).findByToken(any());
        verify(refreshTokenRepository, never()).delete(anyString());
        verify(accessTokenRepository, never()).create(any());

        expectTokenRefreshFailedAuditLog();
    }

    @Test
    public void shouldRefresh_disableRefreshTokenRotation() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId(clientId);

        Client client = new Client();
        client.setId(clientId);
        client.setClientId(clientId);
        client.setDisableRefreshTokenRotation(true);

        String token = "refresh-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(token);
        refreshToken.setToken(token);
        refreshToken.setSubject("subject");
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 10000));

        JWT jwt = new JWT();
        jwt.setJti(token);
        jwt.setAud(clientId);
        jwt.setExp(refreshToken.getExpireAt().getTime() / 1000L);

        when(jwtService.decodeAndVerify(any(), any(Client.class), any())).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));

        TestObserver<Token> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest, client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, never()).delete(anyString());

        expectTokenRefreshAuditLog();
    }

    private EncodedJWT sampleEncodedJwt() {
        return new EncodedJWT("encodedToken", signingCertificateInfo);
    }

    private void expectTokenCreatedAuditLog() {
        auditMatcher = builder -> {
            Audit audit = builder.build(objectMapper);
            AuditOutcomeMatcher outcomeMatcher = new AuditOutcomeMatcher(audit.getOutcome());

            String accessToken = outcomeMatcher.getPropertyByPath("/ACCESS_TOKEN").orElse("");
            String signingCertificateId = outcomeMatcher.getPropertyByPath("/SIGNING_CERTIFICATE_ID").orElse("");
            String signingCertificateName = outcomeMatcher.getPropertyByPath("/SIGNING_CERTIFICATE_NAME").orElse("");

            return Strings.CS.equals(audit.getType(), EventType.TOKEN_CREATED)
                    && audit.getOutcome().getStatus() == Status.SUCCESS
                    && StringUtils.isNotBlank(accessToken)
                    && Strings.CS.equals(signingCertificateId, signingCertificateInfo.certificateId())
                    && Strings.CS.equals(signingCertificateName, signingCertificateInfo.certificateAlias());
        };
    }

    private void expectTokenRefreshAuditLog() {
        auditMatcher = builder -> {
            Audit audit = builder.build(objectMapper);
            AuditOutcomeMatcher outcomeMatcher = new AuditOutcomeMatcher(audit.getOutcome());

            String accessToken = outcomeMatcher.getPropertyByPath("/REFRESH_TOKEN").orElse("");

            return Strings.CS.equals(audit.getType(), EventType.TOKEN_REVOKED) // refresh token used to create new token
                    && audit.getOutcome().getStatus() == Status.SUCCESS
                    && StringUtils.isNotBlank(accessToken);
        };
    }

    private void expectTokenCreatedFailedAuditLog() {
        auditMatcher = builder -> {
            Audit audit = builder.build(objectMapper);
            return Strings.CS.equals(audit.getType(), EventType.TOKEN_CREATED)
                    && audit.getOutcome().getStatus() == Status.FAILURE;
        };
    }

    private void expectTokenRefreshFailedAuditLog() {
        auditMatcher = builder -> {
            Audit audit = builder.build(objectMapper);
            return Strings.CS.equals(audit.getType(), EventType.TOKEN_REVOKED) // refresh token used to create new token
                    && audit.getOutcome().getStatus() == Status.FAILURE;
        };
    }

    private record AuditOutcomeMatcher(AuditOutcome auditOutcome) {
        Optional<String> getPropertyByPath(String path) {
            String jsonPath = "$[?(@.path=='%s')].value".formatted(path);
            return JsonPath.<List<String>>read(auditOutcome.getMessage(), jsonPath)
                    .stream()
                    .findFirst();
        }
    }
}
