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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.consent.UserConsentPostEndpoint;
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
public class AuthorizationDetailsConsentPendingHandlerTest extends RxWebTestBase {

    private static final String CONSENT = "/oauth/consent?client_id=client-id&response_type=code";
    private static final AuthorizationDetail PAYMENT = new AuthorizationDetail(Map.of("type", "payment_initiation"));

    @Mock
    private AuthorizationDetailsConsentService authorizationDetailsConsentService;

    private final Map<String, Object> sessionSeed = new HashMap<>();
    private Session session;
    private ConsentStage stage = ConsentStage.SCOPES;
    private Client client;
    private AuthorizationRequest authorizationRequest;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        client = new Client();
        client.setClientId("client-id");
        User user = new User();
        user.setId("user-id");
        authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setPrompts(Set.of());
        authorizationRequest.setAuthorizationDetails(AuthorizationDetails.of(List.of(PAYMENT)));
        sessionSeed.put(ConstantKeys.USER_CONSENT_APPROVED_KEY, true);

        router.route().order(-2).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().order(-1).handler(rc -> {
            sessionSeed.forEach((key, value) -> rc.session().put(key, value));
            session = rc.session();
            setUser(rc, new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            rc.put(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);
            rc.put(ConstantKeys.CONSENT_STAGE_CONTEXT_KEY, stage);
            rc.put(CONTEXT_PATH, "/test");
            rc.next();
        });
        router.route(HttpMethod.POST, "/oauth/consent")
                .handler(new AuthorizationDetailsConsentPendingHandler(authorizationDetailsConsentService))
                .handler(new UserConsentPostEndpoint());
    }

    @Test
    public void shouldShowTheDetailScreenAfterApprovedScopes() throws Exception {
        when(authorizationDetailsConsentService.getDetailsToPresent(eq(client), any(), any(), eq(false))).thenReturn(Single.just(List.of(PAYMENT)));

        expectRedirectTo("/test/oauth/consent?");
        assertEquals(Boolean.TRUE, session.get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY));
    }

    @Test
    public void shouldReplayTheAuthorizationRequestWhenEveryDetailIsCovered() throws Exception {
        when(authorizationDetailsConsentService.getDetailsToPresent(eq(client), any(), any(), eq(false))).thenReturn(Single.just(List.of()));

        expectRedirectTo("/test/oauth/authorize?");
        assertNull(session.get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY));
    }

    @Test
    public void shouldReplayTheAuthorizationRequestWhenScopesAreDenied() throws Exception {
        sessionSeed.put(ConstantKeys.USER_CONSENT_APPROVED_KEY, false);

        expectRedirectTo("/test/oauth/authorize?");
        verify(authorizationDetailsConsentService, never()).getDetailsToPresent(any(), any(), any(), anyBoolean());
    }

    @Test
    public void shouldReplayTheAuthorizationRequestAfterTheDetailScreen() throws Exception {
        stage = ConsentStage.AUTHORIZATION_DETAILS;

        expectRedirectTo("/test/oauth/authorize?");
        verify(authorizationDetailsConsentService, never()).getDetailsToPresent(any(), any(), any(), anyBoolean());
    }

    @Test
    public void shouldReplayTheAuthorizationRequestWithoutDetails() throws Exception {
        authorizationRequest.setAuthorizationDetails(null);

        expectRedirectTo("/test/oauth/authorize?");
    }

    private void expectRedirectTo(String path) throws Exception {
        testRequest(HttpMethod.POST, CONSENT, null, response -> {
            String location = response.getHeader("location");
            assertTrue(location, location.contains(path));
            assertTrue(location, location.contains("client_id=client-id"));
        }, 302, "Found", null);
    }
}
