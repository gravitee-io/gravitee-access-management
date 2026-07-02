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

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.service.consent.UserConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.common.vertx.web.RoutingContextHelper.setUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserConsentProcessHandlerTest extends RxWebTestBase {

    @Mock
    private UserConsentService userConsentService;

    @Mock
    private Domain domain;

    private Client client;
    private User user;
    private AuthorizationRequest authorizationRequest;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        when(domain.getId()).thenReturn("domain-id");

        client = new Client();
        client.setClientId("client-id");

        user = new User();
        user.setId("user-id");
        user.setUsername("user");
        user.setAdditionalInformation(new HashMap<>());

        authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setTransactionId("tx-id");

        when(userConsentService.saveConsent(any(), anyList(), any()))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(1)));

        LocalSessionStore localSessionStore = LocalSessionStore.create(vertx);
        router.route().order(-2).handler(SessionHandler.create(localSessionStore));
        router.route().order(-1)
                .handler(BodyHandler.create())
                .handler(rc -> {
                    setUser(rc, new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user));
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.put(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);
                    rc.next();
                });

        router.route(HttpMethod.POST, "/oauth/consent")
                .handler(new UserConsentProcessHandler(userConsentService, domain))
                .handler(rc -> rc.response().setStatusCode(200).end())
                .failureHandler(rc -> {
                    if (rc.failure() instanceof OAuth2Exception oAuth2Exception) {
                        rc.response().setStatusCode(oAuth2Exception.getHttpStatusCode()).end(oAuth2Exception.getOAuth2ErrorCode());
                    } else {
                        rc.response().setStatusCode(500).end();
                    }
                });
    }

    private List<ScopeApproval> captureSavedApprovals() {
        ArgumentCaptor<List<ScopeApproval>> captor = ArgumentCaptor.forClass(List.class);
        verify(userConsentService).saveConsent(any(), captor.capture(), any());
        return captor.getValue();
    }

    private Set<String> approvedScopes(List<ScopeApproval> approvals) {
        return approvals.stream()
                .filter(a -> a.getStatus() == ScopeApproval.ApprovalStatus.APPROVED)
                .map(ScopeApproval::getScope)
                .collect(Collectors.toSet());
    }

    private Set<String> deniedScopes(List<ScopeApproval> approvals) {
        return approvals.stream()
                .filter(a -> a.getStatus() == ScopeApproval.ApprovalStatus.DENIED)
                .map(ScopeApproval::getScope)
                .collect(Collectors.toSet());
    }

    @Test
    public void shouldApproveOnlySelectedScopes_partialConsent() throws Exception {
        authorizationRequest.setScopes(new HashSet<>(Set.of("read", "write")));
        when(userConsentService.checkConsent(any(), any())).thenReturn(Single.just(Collections.emptySet()));

        testRequest(HttpMethod.POST, "/oauth/consent",
                req -> writeForm(req, "scope.read=true&user_oauth_approval=true"),
                HttpStatusCode.OK_200, "OK", null);

        List<ScopeApproval> approvals = captureSavedApprovals();
        assertEquals(Set.of("read"), approvedScopes(approvals));
        assertEquals(Set.of("write"), deniedScopes(approvals));
        assertTrue(authorizationRequest.isApproved());
        assertEquals(Set.of("read"), authorizationRequest.getScopes());
    }

    @Test
    public void shouldApproveAllScopes_legacyAcceptAll() throws Exception {
        // legacy template submits a fixed scope.<key>=true hidden field per scope
        authorizationRequest.setScopes(new HashSet<>(Set.of("read", "write")));
        when(userConsentService.checkConsent(any(), any())).thenReturn(Single.just(Collections.emptySet()));

        testRequest(HttpMethod.POST, "/oauth/consent",
                req -> writeForm(req, "scope.read=true&scope.write=true&user_oauth_approval=true"),
                HttpStatusCode.OK_200, "OK", null);

        List<ScopeApproval> approvals = captureSavedApprovals();
        assertEquals(Set.of("read", "write"), approvedScopes(approvals));
        assertTrue(deniedScopes(approvals).isEmpty());
        assertTrue(authorizationRequest.isApproved());
    }

    @Test
    public void shouldDenyAllScopes_userRejected() throws Exception {
        // Cancel (legacy) and Deny (new) both submit user_oauth_approval=false
        authorizationRequest.setScopes(new HashSet<>(Set.of("read", "write")));
        when(userConsentService.checkConsent(any(), any())).thenReturn(Single.just(Collections.emptySet()));

        testRequest(HttpMethod.POST, "/oauth/consent",
                req -> writeForm(req, "scope.read=true&scope.write=true&user_oauth_approval=false"),
                HttpStatusCode.OK_200, "OK", null);

        List<ScopeApproval> approvals = captureSavedApprovals();
        assertTrue(approvedScopes(approvals).isEmpty());
        assertEquals(Set.of("read", "write"), deniedScopes(approvals));
        assertFalse(authorizationRequest.isApproved());
    }

    @Test
    public void shouldIgnoreScopesNotInTheAuthorizationRequest_tamperResistance() throws Exception {
        authorizationRequest.setScopes(new HashSet<>(Set.of("read")));
        when(userConsentService.checkConsent(any(), any())).thenReturn(Single.just(Collections.emptySet()));

        testRequest(HttpMethod.POST, "/oauth/consent",
                req -> writeForm(req, "scope.read=true&scope.admin=true&user_oauth_approval=true"),
                HttpStatusCode.OK_200, "OK", null);

        List<ScopeApproval> approvals = captureSavedApprovals();
        // admin was never requested: it must not be approved nor recorded at all
        assertEquals(Set.of("read"), approvedScopes(approvals));
        assertTrue(approvals.stream().noneMatch(a -> "admin".equals(a.getScope())));
        assertEquals(Set.of("read"), authorizationRequest.getScopes());
    }

    @Test
    public void shouldOnlyProcessPresentedScopes_reuseDoesNotClobberPreviousConsent() throws Exception {
        // read+write already approved previously, app now also requests "profile" -> only profile is presented
        authorizationRequest.setScopes(new HashSet<>(Set.of("read", "write", "profile")));
        when(userConsentService.checkConsent(any(), any())).thenReturn(Single.just(Set.of("read", "write")));

        testRequest(HttpMethod.POST, "/oauth/consent",
                req -> writeForm(req, "scope.profile=true&user_oauth_approval=true"),
                HttpStatusCode.OK_200, "OK", null);

        List<ScopeApproval> approvals = captureSavedApprovals();
        // only the presented delta (profile) is persisted; read/write are left untouched
        assertEquals(Set.of("profile"), approvedScopes(approvals));
        assertTrue(approvals.stream().noneMatch(a -> "read".equals(a.getScope()) || "write".equals(a.getScope())));
    }

    @Test
    public void shouldPresentAllScopes_promptConsent() throws Exception {
        // prompt=consent re-presents every requested scope, so checkConsent must not be consulted
        authorizationRequest.setScopes(new HashSet<>(Set.of("read", "write")));
        authorizationRequest.setPrompts(Set.of("consent"));

        testRequest(HttpMethod.POST, "/oauth/consent",
                req -> writeForm(req, "scope.read=true&user_oauth_approval=true"),
                HttpStatusCode.OK_200, "OK", null);

        List<ScopeApproval> approvals = captureSavedApprovals();
        assertEquals(Set.of("read"), approvedScopes(approvals));
        assertEquals(Set.of("write"), deniedScopes(approvals));
    }

    @Test
    public void shouldApproveRequiredScope_whenSubmittedTrue() throws Exception {
        authorizationRequest.setScopes(new HashSet<>(Set.of("read", "admin")));
        client.setScopeSettings(List.of(requiredScopeSetting("admin")));
        when(userConsentService.checkConsent(any(), any())).thenReturn(Single.just(Collections.emptySet()));

        testRequest(HttpMethod.POST, "/oauth/consent",
                req -> writeForm(req, "scope.read=true&scope.admin=true&user_oauth_approval=true"),
                HttpStatusCode.OK_200, "OK", null);

        List<ScopeApproval> approvals = captureSavedApprovals();
        assertEquals(Set.of("read", "admin"), approvedScopes(approvals));
        assertTrue(deniedScopes(approvals).isEmpty());
        assertEquals(Set.of("read", "admin"), authorizationRequest.getScopes());
    }

    @Test
    public void shouldRejectWithAccessDenied_whenRequiredScopeIsMissing() throws Exception {
        // a presented required scope with no submitted value means it was not shown/approved
        // (rendering issue or tampering): consent must not be persisted
        authorizationRequest.setScopes(new HashSet<>(Set.of("read", "admin")));
        client.setScopeSettings(List.of(requiredScopeSetting("admin")));
        when(userConsentService.checkConsent(any(), any())).thenReturn(Single.just(Collections.emptySet()));

        testRequest(HttpMethod.POST, "/oauth/consent",
                req -> writeForm(req, "scope.read=true&user_oauth_approval=true"),
                403, "Forbidden", "access_denied");

        verify(userConsentService, never()).saveConsent(any(), anyList(), any());
        assertFalse(authorizationRequest.isApproved());
    }

    @Test
    public void shouldRejectWithAccessDenied_whenRequiredScopeIsSubmittedFalse() throws Exception {
        // a tampered payload sets the required scope to false
        authorizationRequest.setScopes(new HashSet<>(Set.of("read", "admin")));
        client.setScopeSettings(List.of(requiredScopeSetting("admin")));
        when(userConsentService.checkConsent(any(), any())).thenReturn(Single.just(Collections.emptySet()));

        testRequest(HttpMethod.POST, "/oauth/consent",
                req -> writeForm(req, "scope.read=true&scope.admin=false&user_oauth_approval=true"),
                403, "Forbidden", "access_denied");

        verify(userConsentService, never()).saveConsent(any(), anyList(), any());
    }

    @Test
    public void shouldDenyRequiredScope_whenUserRejectsOverall() throws Exception {
        // the required-scope check is skipped on an outright rejection: everything is denied, no access_denied
        authorizationRequest.setScopes(new HashSet<>(Set.of("read", "admin")));
        client.setScopeSettings(List.of(requiredScopeSetting("admin")));
        when(userConsentService.checkConsent(any(), any())).thenReturn(Single.just(Collections.emptySet()));

        testRequest(HttpMethod.POST, "/oauth/consent",
                req -> writeForm(req, "user_oauth_approval=false"),
                HttpStatusCode.OK_200, "OK", null);

        List<ScopeApproval> approvals = captureSavedApprovals();
        assertTrue(approvedScopes(approvals).isEmpty());
        assertEquals(Set.of("read", "admin"), deniedScopes(approvals));
        assertFalse(authorizationRequest.isApproved());
    }

    private static ApplicationScopeSettings requiredScopeSetting(String scope) {
        ApplicationScopeSettings settings = new ApplicationScopeSettings(scope);
        settings.setRequiredScope(true);
        return settings;
    }

    private void writeForm(io.vertx.rxjava3.core.http.HttpClientRequest req, String body) {
        req.putHeader("content-type", "application/x-www-form-urlencoded");
        req.putHeader("content-length", String.valueOf(body.length()));
        req.write(Buffer.buffer(body));
    }
}
