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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailDigest;
import io.gravitee.am.gateway.handler.oauth2.service.consent.AuthorizationDetailsConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oauth2.AuthorizationDetail;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.Session;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.web.RoutingContextHelper.setUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationRequestAuthorizationDetailsConsentHandlerTest extends RxWebTestBase {

    private static final String AUTHORIZE = "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback";
    private static final AuthorizationDetail PAYMENT = new AuthorizationDetail(Map.of("type", "payment_initiation", "amount", "10"));

    @Mock
    private AuthorizationDetailsConsentService authorizationDetailsConsentService;

    private final Map<String, Object> sessionSeed = new HashMap<>();
    private Session session;
    private Client client;
    private User user;
    private AuthorizationRequest authorizationRequest;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        client = new Client();
        client.setClientId("client-id");
        user = new User();
        user.setId("user-id");
        authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setPrompts(Set.of());

        router.route().order(-2).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().order(-1).handler(rc -> {
            sessionSeed.forEach((key, value) -> rc.session().put(key, value));
            session = rc.session();
            if (user != null) {
                setUser(rc, new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user));
            }
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            rc.put(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);
            rc.put(CONTEXT_PATH, "/test");
            rc.next();
        });
        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(new AuthorizationRequestAuthorizationDetailsConsentHandler(authorizationDetailsConsentService))
                .handler(rc -> rc.response().end())
                .failureHandler(rc -> rc.response().setStatusCode(400)
                        .end(rc.failure() instanceof OAuth2Exception e ? e.getOAuth2ErrorCode() : "unexpected"));
    }

    @Test
    public void shouldContinueWithoutAuthorizationDetails() throws Exception {
        testRequest(HttpMethod.GET, AUTHORIZE, 200, "OK");

        verify(authorizationDetailsConsentService, never()).getDetailsToPresent(any(), any(), any(), anyBoolean());
    }

    @Test
    public void shouldContinueWhenNothingIsLeftToConsentTo() throws Exception {
        givenRequestedDetails();
        when(authorizationDetailsConsentService.getDetailsToPresent(eq(client), any(), any(), eq(false))).thenReturn(Single.just(List.of()));

        testRequest(HttpMethod.GET, AUTHORIZE, 200, "OK");
    }

    @Test
    public void shouldRedirectToTheConsentPage() throws Exception {
        givenRequestedDetails();
        when(authorizationDetailsConsentService.getDetailsToPresent(eq(client), any(), any(), eq(false))).thenReturn(Single.just(List.of(PAYMENT)));

        testRequest(HttpMethod.GET, AUTHORIZE, null, resp -> {
            assertTrue(resp.headers().get("location").contains("/test/oauth/consent"));
            assertTrue(resp.headers().get("location").contains("client_id=client-id"));
        }, 302, "Found", null);
        assertEquals(Boolean.TRUE, session.get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY));
    }

    @Test
    public void shouldAskTheServiceForEveryDetailWithPromptConsent() throws Exception {
        givenRequestedDetails();
        authorizationRequest.setPrompts(Set.of("consent"));
        when(authorizationDetailsConsentService.getDetailsToPresent(eq(client), any(), any(), eq(true))).thenReturn(Single.just(List.of(PAYMENT)));

        testRequest(HttpMethod.GET, AUTHORIZE, 302, "Found");
    }

    @Test
    public void shouldRequireInteractionWithPromptNone() throws Exception {
        givenRequestedDetails();
        authorizationRequest.setPrompts(Set.of("none"));
        when(authorizationDetailsConsentService.getDetailsToPresent(eq(client), any(), any(), eq(false))).thenReturn(Single.just(List.of(PAYMENT)));

        testRequest(HttpMethod.GET, AUTHORIZE, 400, "Bad Request", "interaction_required");
    }

    @Test
    public void shouldContinueAfterTheUserApprovedTheSameDetails() throws Exception {
        givenRequestedDetails();
        sessionSeed.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_COMPLETED_KEY, AuthorizationDetailDigest.ofAll(List.of(PAYMENT)));
        sessionSeed.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_APPROVED_KEY, true);

        testRequest(HttpMethod.GET, AUTHORIZE, 200, "OK");

        verify(authorizationDetailsConsentService, never()).getDetailsToPresent(any(), any(), any(), anyBoolean());
        assertEquals(List.of(PAYMENT), authorizationRequest.getAuthorizationDetails().getAll());
    }

    @Test
    public void shouldDenyAccessAfterTheUserRejected() throws Exception {
        givenRequestedDetails();
        sessionSeed.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_COMPLETED_KEY, AuthorizationDetailDigest.ofAll(List.of(PAYMENT)));
        sessionSeed.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_APPROVED_KEY, false);

        testRequest(HttpMethod.GET, AUTHORIZE, 400, "Bad Request", "access_denied");
    }

    @Test
    public void shouldDenyAccessWhenTheDetailsChangedAfterApproval() throws Exception {
        givenRequestedDetails();
        sessionSeed.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_COMPLETED_KEY,
                AuthorizationDetailDigest.ofAll(List.of(new AuthorizationDetail(Map.of("type", "payment_initiation", "amount", "1")))));
        sessionSeed.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_APPROVED_KEY, true);

        testRequest(HttpMethod.GET, AUTHORIZE, 400, "Bad Request", "access_denied");
    }

    @Test
    public void shouldDenyAccessWithoutAnAuthenticatedUser() throws Exception {
        givenRequestedDetails();
        user = null;

        testRequest(HttpMethod.GET, AUTHORIZE, 400, "Bad Request", "access_denied");
    }

    private void givenRequestedDetails() {
        authorizationRequest.setAuthorizationDetails(AuthorizationDetails.of(List.of(PAYMENT)));
    }
}
