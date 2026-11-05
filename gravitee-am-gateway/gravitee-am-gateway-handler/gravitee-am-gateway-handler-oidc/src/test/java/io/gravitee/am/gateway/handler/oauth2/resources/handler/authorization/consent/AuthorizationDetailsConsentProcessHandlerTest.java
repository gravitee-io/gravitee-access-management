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
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailDigest;
import io.gravitee.am.gateway.handler.oauth2.service.consent.AuthorizationDetailsConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oauth2.AuthorizationDetail;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import io.vertx.rxjava3.ext.web.Session;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
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

import static io.gravitee.am.gateway.handler.common.vertx.web.RoutingContextHelper.setUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationDetailsConsentProcessHandlerTest extends RxWebTestBase {

    private static final AuthorizationDetail PAYMENT = new AuthorizationDetail(Map.of("type", "payment_initiation", "amount", "10"));
    private static final AuthorizationDetail ACCOUNT = new AuthorizationDetail(Map.of("type", "account_information"));

    @Mock
    private AuthorizationDetailsConsentService authorizationDetailsConsentService;

    @Mock
    private Domain domain;

    private final Map<String, Object> sessionSeed = new HashMap<>();
    private Session session;
    private Client client;
    private AuthorizationRequest authorizationRequest;
    private AuthorizationDetails requested;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        when(domain.getId()).thenReturn("domain-id");
        client = new Client();
        client.setClientId("client-id");
        User user = new User();
        user.setId("user-id");
        user.setUsername("user");
        user.setAdditionalInformation(new HashMap<>());
        requested = AuthorizationDetails.of(List.of(PAYMENT, ACCOUNT));
        authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setPrompts(Set.of());
        authorizationRequest.setAuthorizationDetails(requested);
        sessionSeed.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY, true);

        when(authorizationDetailsConsentService.saveConsent(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(Completable.complete());

        router.route().order(-2).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().order(-1)
                .handler(BodyHandler.create())
                .handler(rc -> {
                    sessionSeed.forEach((key, value) -> rc.session().put(key, value));
                    session = rc.session();
                    setUser(rc, new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user));
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.put(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);
                    rc.next();
                });
        router.route(HttpMethod.POST, "/oauth/consent")
                .handler(new AuthorizationDetailsConsentProcessHandler(authorizationDetailsConsentService, domain))
                .handler(rc -> rc.response().end())
                .failureHandler(rc -> rc.response().setStatusCode(500).end());
    }

    @Test
    public void shouldRememberAnApproval() throws Exception {
        givenPresented(List.of(ACCOUNT));

        submit("user_oauth_approval=true", 200, "OK");

        verify(authorizationDetailsConsentService).saveConsent(eq(client), any(), any(), eq(requested), eq(List.of(ACCOUNT)), eq(true));
        assertDecision(true);
    }

    @Test
    public void shouldRecordARejection() throws Exception {
        givenPresented(List.of(ACCOUNT));

        submit("user_oauth_approval=false", 200, "OK");

        verify(authorizationDetailsConsentService).saveConsent(eq(client), any(), any(), eq(requested), eq(List.of(ACCOUNT)), eq(false));
        assertDecision(false);
    }

    @Test
    public void shouldRejectASubmissionForDetailsOtherThanThosePresented() throws Exception {
        givenPresented(List.of(ACCOUNT));
        sessionSeed.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PRESENTED_KEY, AuthorizationDetailDigest.ofAll(List.of(PAYMENT)));

        submit("user_oauth_approval=true", 200, "OK");

        verify(authorizationDetailsConsentService).saveConsent(eq(client), any(), any(), eq(requested), eq(List.of(ACCOUNT)), eq(false));
        assertDecision(false);
    }

    @Test
    public void shouldRejectASubmissionWithoutAPresentedScreen() throws Exception {
        when(authorizationDetailsConsentService.getDetailsToPresent(eq(client), any(), eq(requested), eq(false))).thenReturn(Single.just(List.of(ACCOUNT)));

        submit("user_oauth_approval=true", 200, "OK");

        verify(authorizationDetailsConsentService).saveConsent(eq(client), any(), any(), eq(requested), eq(List.of(ACCOUNT)), eq(false));
        assertDecision(false);
    }

    @Test
    public void shouldSkipARequestWithoutDetails() throws Exception {
        authorizationRequest.setAuthorizationDetails(null);

        submit("user_oauth_approval=true", 200, "OK");

        verify(authorizationDetailsConsentService, never()).saveConsent(any(), any(), any(), any(), any(), anyBoolean());
        assertNull(session.get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_COMPLETED_KEY));
        assertNull(session.get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY));
    }

    @Test
    public void shouldFailWhenTheConsentCannotBeSaved() throws Exception {
        givenPresented(List.of(ACCOUNT));
        when(authorizationDetailsConsentService.saveConsent(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(Completable.error(new IllegalStateException("unavailable")));

        submit("user_oauth_approval=true", 500, "Internal Server Error");

        assertNull(session.get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_COMPLETED_KEY));
    }

    private void givenPresented(List<AuthorizationDetail> presented) {
        when(authorizationDetailsConsentService.getDetailsToPresent(eq(client), any(), eq(requested), eq(false))).thenReturn(Single.just(presented));
        sessionSeed.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PRESENTED_KEY, AuthorizationDetailDigest.ofAll(presented));
    }

    private void assertDecision(boolean approved) {
        assertEquals(AuthorizationDetailDigest.ofAll(requested.getAll()), session.get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_COMPLETED_KEY));
        assertEquals(approved, session.get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_APPROVED_KEY));
        assertNull(session.get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY));
        assertNull(session.get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PRESENTED_KEY));
    }

    private void submit(String form, int status, String message) throws Exception {
        testRequest(HttpMethod.POST, "/oauth/consent", request -> writeForm(request, form), status, message, null);
    }

    private static void writeForm(HttpClientRequest request, String form) {
        Buffer body = Buffer.buffer(form);
        request.putHeader("content-type", "application/x-www-form-urlencoded");
        request.putHeader("content-length", String.valueOf(body.length()));
        request.write(body);
    }
}
