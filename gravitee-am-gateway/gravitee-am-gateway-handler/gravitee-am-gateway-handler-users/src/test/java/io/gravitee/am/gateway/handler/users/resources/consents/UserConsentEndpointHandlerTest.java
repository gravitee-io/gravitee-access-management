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
package io.gravitee.am.gateway.handler.users.resources.consents;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.users.service.DomainUserConsentService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.ScopeApprovalNotFoundException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_IP_LOCATION;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_USER_AGENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserConsentEndpointHandlerTest extends RxWebTestBase {


    private final DomainUserConsentService userService = mock();

    private final ClientSyncService clientService = mock();

    private final SubjectManager subjectManager = mock();

    private final Domain domain = new Domain();

    private UserConsentEndpointHandler underTest = new UserConsentEndpointHandler(userService, clientService, domain, subjectManager);

    @Mock
    private OAuth2AuthProvider oAuth2AuthProvider;

    private final OAuth2AuthHandler oAuth2AuthHandler = OAuth2AuthHandler.create(oAuth2AuthProvider);

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void shouldNotGetConsent_no_token() throws Exception {
        router.route("/users/:userId/consents/:consentId")
                .handler(oAuth2AuthHandler)
                .handler(underTest::get)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.GET, "/users/user-id/consents/consent-id",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");

    }

    // TODO : need to mock Async Handler of the oauth2AuthHandler
    @Test
    @Ignore
    public void shouldNotGetConsent_invalid_token() throws Exception {
        router.route("/users/:userId/consents/:consentId")
                .handler(oAuth2AuthHandler)
                .handler(underTest::get)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.GET, "/users/user-id/consents/consent-id",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer token"),
                401,
                "Unauthorized", null);
    }

    @Test
    public void shouldNotGetConsent_notFound() throws Exception {
        when(userService.consent(anyString())).thenReturn(Maybe.error(new ScopeApprovalNotFoundException("consentId")));

        router.route("/users/:userId/consents/:consentId")
                .handler(underTest::get)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.GET, "/users/user-id/consents/consent-id",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer token"),
                404,
                "Not Found", null);

    }

    @Test
    public void shouldGetConsent() throws Exception {
        when(userService.consent(anyString())).thenReturn(Maybe.just(new ScopeApproval()));

        router.route("/users/:userId/consents/:consentId")
                .handler(underTest::get)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.GET, "/users/user-id/consents/consent-id",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer token"),
                200,
                "OK", null);

    }

    @Test
    public void shouldRevokeConsent() throws Exception {
        when(subjectManager.findUserBySub(any())).thenReturn(Maybe.just(new User()));
        when(subjectManager.findUserIdBySub(any())).thenReturn(Maybe.just(UserId.internal("user-id")));
        when(userService.revokeConsent(any(), any(), any())).thenReturn(Completable.complete());

        router.route("/users/:userId/consents/:consentId")
                .handler(givenContextHas(ConstantKeys.TOKEN_CONTEXT_KEY, token("sub", null)))
                .handler(underTest::revoke)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.DELETE, "/users/user-id/consents/consent-id",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer token"),
                204,
                "No Content", null);
    }


    // for sonar branch coverage mostly
    @Test
    public void revokeConsent_shouldStoreIp() throws Exception {
        when(subjectManager.findUserBySub(any())).thenReturn(Maybe.just(new User()));
        router.route("/users/:userId/consents/:consentId")
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(rc -> {
                    rc.session().put(USER_CONSENT_IP_LOCATION, true).put(USER_CONSENT_USER_AGENT, true);
                    rc.next();
                })
                .handler(givenContextHas(ConstantKeys.TOKEN_CONTEXT_KEY, token("sub", null)))
                .handler(rc -> {
                    var principal = underTest.getPrincipal(rc)
                            .test()
                            .awaitDone(1, TimeUnit.SECONDS)
                            .assertValueCount(1)
                            .values()
                            .get(0);
                    AssertionsForInterfaceTypes.assertThat(principal.getAdditionalInformation())
                            .containsKey(Claims.IP_ADDRESS)
                            .containsKey(Claims.USER_AGENT);
                    rc.response().setStatusCode(204).setStatusMessage("No Content").end();
                })
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.DELETE, "/users/user-id/consents/consent-id",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer token"),
                204,
                "No Content", null);
    }

    // for sonar branch coverage mostly
    @Test
    public void revokeConsent_byAud_shouldStoreIp() throws Exception {
        when(clientService.findByClientId(any())).thenReturn(Maybe.just(new Client()));

        router.route("/users/:userId/consents/:consentId")
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(rc -> {
                    rc.session().put(USER_CONSENT_IP_LOCATION, true).put(USER_CONSENT_USER_AGENT, true);
                    rc.next();
                })
                .handler(givenContextHas(ConstantKeys.TOKEN_CONTEXT_KEY, token("test", "test")))
                .handler(rc -> {
                    var principal = underTest.getPrincipal(rc)
                            .test()
                            .awaitDone(1, TimeUnit.SECONDS)
                            .assertValueCount(1)
                            .values()
                            .get(0);
                    AssertionsForInterfaceTypes.assertThat(principal.getAdditionalInformation())
                            .containsKey(Claims.IP_ADDRESS)
                            .containsKey(Claims.USER_AGENT);
                    rc.response().setStatusCode(204).setStatusMessage("No Content").end();
                })
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.DELETE, "/users/user-id/consents/consent-id",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer token"),
                204,
                "No Content", null);
    }

    // for sonar branch coverage mostly
    @Test
    public void shouldRevokeConsent_byAud() throws Exception {
        when(subjectManager.findUserIdBySub(any())).thenReturn(Maybe.just(UserId.internal("user-id")));
        when(userService.revokeConsent(any(), any(), any())).thenReturn(Completable.complete());
        when(clientService.findByClientId(any())).thenReturn(Maybe.just(new Client()));

        router.route("/users/:userId/consents/:consentId")
                .handler(givenContextHas(ConstantKeys.TOKEN_CONTEXT_KEY, token("sub", "sub")))
                .handler(underTest::revoke)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.DELETE, "/users/user-id/consents/consent-id",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer token"),
                204,
                "No Content", null);
    }

    @Test
    public void tokenWithoutSub() throws Exception {
        router.route("/users/:userId/consents/:consentId")
                .handler(givenContextHas(ConstantKeys.TOKEN_CONTEXT_KEY, token(null, "aud")))
                .handler(rc -> {
                    underTest.getPrincipal(rc)
                            .test().awaitDone(5, TimeUnit.SECONDS)
                            .assertValue(u -> u.getUsername().equals("unknown-user"));
                    rc.next();
                })
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.DELETE, "/users/user-id/consents/consent-id",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer token"),
                404,
                "Not Found", null);
    }

    @Test
    public void subjectManager_illegalArgumentException_ignored() {
        when(subjectManager.findUserIdBySub(any())).thenReturn(Maybe.error(new IllegalArgumentException()));

        underTest.getUserIdFromSub(token("sub", "aud"))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertValue(id -> id.id().equals("sub"));
    }

    @Test
    public void subjectManager_otherException_passedOn() {
        when(subjectManager.findUserIdBySub(any())).thenReturn(Maybe.error(new RuntimeException("some random error")));

        underTest.getUserIdFromSub(token("sub", "aud"))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertError(RuntimeException.class);
    }

    @Test
    public void idMatch() {
        when(subjectManager.generateInternalSubFrom(any(UserId.class))).thenAnswer(inv->{
            var id = (UserId) inv.getArguments()[0];

            return  id.hasExternal() ? (id.source() + ":" + id.externalId()) : id.id();
        });

        var internalTestId = UserId.internal("test");
        var fullTestId = new UserId("test", "a", "idp-1");
        var externalId = new UserId(null, "a", "idp-1");

        Assertions.assertThat(underTest.userIdParamMatchTokenIdentity(internalTestId,"test", token("test",null))).isTrue();

        Assertions.assertThat(underTest.userIdParamMatchTokenIdentity(fullTestId,"test", token("test",null))).isTrue();
        Assertions.assertThat(underTest.userIdParamMatchTokenIdentity(fullTestId,"idp-1:a", token("test",null))).isTrue();
        Assertions.assertThat(underTest.userIdParamMatchTokenIdentity(fullTestId, "idp-1:b", token("something-else", "some-aud"))).isFalse();

        Assertions.assertThat(underTest.userIdParamMatchTokenIdentity(externalId,"idp-1:a", token("md5(gis)",null))).isTrue();
        Assertions.assertThat(underTest.userIdParamMatchTokenIdentity(externalId,"test", token("md5(gis)",null))).isFalse();


    }

    private JWT token(String sub, String aud) {
        JWT token = new JWT();
        if (sub != null) token.setSub(sub);
        if (aud != null) token.setAud(aud);
        return token;
    }

}
