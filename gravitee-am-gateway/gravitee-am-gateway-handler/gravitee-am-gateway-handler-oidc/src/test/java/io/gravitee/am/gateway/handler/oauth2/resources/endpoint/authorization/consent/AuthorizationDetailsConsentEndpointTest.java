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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.consent;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailDigest;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailTypeManager;
import io.gravitee.am.gateway.handler.oauth2.service.consent.AuthorizationDetailsConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oauth2.AuthorizationDetail;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.AuthorizationDetailTypeProperties;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.gravitee.am.gateway.handler.common.vertx.web.RoutingContextHelper.setUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationDetailsConsentEndpointTest extends RxWebTestBase {

    private static final AuthorizationDetail PAYMENT = new AuthorizationDetail(Map.of("type", "payment_initiation", "amount", "10"));
    private static final AuthorizationDetail ACCOUNT = new AuthorizationDetail(Map.of("type", "account_information"));

    @Mock
    private AuthorizationDetailsConsentService authorizationDetailsConsentService;

    @Mock
    private AuthorizationDetailTypeManager authorizationDetailTypeManager;

    @Mock
    private ThymeleafTemplateEngine templateEngine;

    @Mock
    private Domain domain;

    private Client client;
    private AuthorizationRequest authorizationRequest;
    private RoutingContext capturedContext;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        client = new Client();
        client.setId("client-internal-id");
        client.setClientId("client-id");
        User user = new User();
        user.setId("user-id");
        authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setPrompts(Set.of());
        authorizationRequest.setAuthorizationDetails(AuthorizationDetails.of(List.of(PAYMENT, ACCOUNT)));

        when(templateEngine.render(anyMap(), any())).thenReturn(Single.just(Buffer.buffer()));

        router.route().order(-2).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().order(-1).handler(rc -> {
            setUser(rc, new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            rc.put(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);
            rc.next();
        });
        router.route(HttpMethod.GET, "/oauth/consent").handler(rc -> {
            capturedContext = rc;
            new AuthorizationDetailsConsentEndpoint(authorizationDetailsConsentService, authorizationDetailTypeManager, templateEngine, domain).handle(rc);
        });
    }

    @Test
    public void shouldRenderTheDetailsLeftToConsentTo() throws Exception {
        when(authorizationDetailsConsentService.getDetailsToPresent(eq(client), any(), eq(authorizationRequest.getAuthorizationDetails()), eq(false)))
                .thenReturn(Single.just(List.of(ACCOUNT)));

        testRequest(HttpMethod.GET, "/oauth/consent?client_id=client-id", 200, "OK");

        AuthorizationDetails rendered = capturedContext.get(ConstantKeys.AUTHORIZATION_DETAILS_CONTEXT_KEY);
        assertEquals(List.of(ACCOUNT), rendered.getAll());
        assertEquals(AuthorizationDetailDigest.ofAll(List.of(ACCOUNT)),
                capturedContext.session().get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PRESENTED_KEY));
        assertTrue(((String) capturedContext.get(ConstantKeys.ACTION_KEY)).endsWith("/oauth/consent?client_id=client-id"));
    }

    @Test
    public void shouldRenderTheNamesOfThePresentedTypes() throws Exception {
        AuthorizationDetailType payment = new AuthorizationDetailType();
        payment.setName("Payment initiation");
        payment.setDescription("Initiate a payment");
        when(authorizationDetailTypeManager.findByType("payment_initiation")).thenReturn(Optional.of(payment));
        when(authorizationDetailTypeManager.findByType("account_information")).thenReturn(Optional.empty());
        when(authorizationDetailsConsentService.getDetailsToPresent(any(), any(), any(), eq(false))).thenReturn(Single.just(List.of(PAYMENT, ACCOUNT)));

        testRequest(HttpMethod.GET, "/oauth/consent", 200, "OK");

        Map<String, AuthorizationDetailTypeProperties> types = capturedContext.get(ConstantKeys.AUTHORIZATION_DETAIL_TYPES_CONTEXT_KEY);
        assertEquals(Set.of("payment_initiation"), types.keySet());
        assertEquals("Payment initiation", types.get("payment_initiation").getName());
        assertEquals("Initiate a payment", types.get("payment_initiation").getDescription());
    }

    @Test
    public void shouldRenderTheApplicationForm() throws Exception {
        when(authorizationDetailsConsentService.getDetailsToPresent(any(), any(), any(), eq(false))).thenReturn(Single.just(List.of(PAYMENT)));

        testRequest(HttpMethod.GET, "/oauth/consent", 200, "OK");

        ArgumentCaptor<String> templateName = ArgumentCaptor.forClass(String.class);
        verify(templateEngine).render(anyMap(), templateName.capture());
        assertEquals("oauth2_authorization_details_consent|client-internal-id", templateName.getValue());
    }

    @Test
    public void shouldAskForEveryDetailWithPromptConsent() throws Exception {
        authorizationRequest.setPrompts(Set.of("consent"));
        when(authorizationDetailsConsentService.getDetailsToPresent(any(), any(), any(), eq(true))).thenReturn(Single.just(List.of(PAYMENT, ACCOUNT)));

        testRequest(HttpMethod.GET, "/oauth/consent", 200, "OK");

        AuthorizationDetails rendered = capturedContext.get(ConstantKeys.AUTHORIZATION_DETAILS_CONTEXT_KEY);
        assertEquals(List.of(PAYMENT, ACCOUNT), rendered.getAll());
    }
}
