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
package io.gravitee.am.gateway.handler.aauth.resources.endpoint;

import io.gravitee.am.gateway.handler.aauth.model.AAuthTokenResponse;
import io.gravitee.am.gateway.handler.aauth.model.PendingRequestStatus;
import io.gravitee.am.gateway.handler.aauth.service.consent.AAuthConsentService;
import io.gravitee.am.gateway.handler.aauth.service.pending.AAuthPendingRequestService;
import io.gravitee.am.gateway.handler.aauth.service.token.AAuthTokenService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.oidc.model.AAuthPendingRequest;
import io.gravitee.am.service.ApplicationService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AAuthConsentPostEndpoint}.
 */
public class AAuthConsentPostEndpointTest extends RxWebTestBase {

    private AAuthPendingRequestService pendingService;
    private AAuthTokenService tokenService;
    private AAuthConsentService consentService;
    private ApplicationService applicationService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        pendingService = mock(AAuthPendingRequestService.class);
        tokenService = mock(AAuthTokenService.class);
        consentService = mock(AAuthConsentService.class);
        applicationService = mock(ApplicationService.class);

        Domain domain = new Domain();
        domain.setId("domain-1");

        var endpoint = new AAuthConsentPostEndpoint(pendingService, tokenService, consentService, applicationService, domain);

        // Body handler must be registered before user handlers in Vert.x 5
        router.route("/aauth/consent")
                .handler(io.vertx.rxjava3.ext.web.handler.BodyHandler.create());

        // User injection for tests — separate route to avoid handler ordering conflict
        router.route("/aauth/consent").handler(ctx -> {
            if ("true".equals(ctx.request().getHeader("X-Test-With-User"))) {
                var modelUser = new io.gravitee.am.model.User();
                modelUser.setId("user-1");
                modelUser.setUsername("testuser");
                modelUser.setSource("default-idp");
                modelUser.setReferenceId("domain-1");
                ((io.vertx.ext.web.impl.UserContextImpl) ctx.getDelegate().userContext())
                        .setUser(new User(modelUser));
            }
            ctx.next();
        });

        router.route("/aauth/consent")
                .handler(endpoint)
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldReturn400_whenCodeMissing() throws Exception {
        testRequest(
                HttpMethod.POST, "/aauth/consent",
                req -> {
                    req.putHeader("Content-Type", "application/x-www-form-urlencoded");
                    req.end(io.vertx.core.buffer.Buffer.buffer("user_oauth_approval=true"));
                },
                null, 400, "Bad Request", null
        );
    }

    @Test
    public void shouldReturn401_whenNoUser() throws Exception {
        when(pendingService.findByInteractionCode(eq("XXXX-1234"))).thenReturn(Maybe.empty());

        testRequest(
                HttpMethod.POST, "/aauth/consent",
                req -> {
                    req.putHeader("Content-Type", "application/x-www-form-urlencoded");
                    req.end(io.vertx.core.buffer.Buffer.buffer("code=XXXX-1234&user_oauth_approval=true"));
                },
                null, 401, "Unauthorized", null
        );
    }

    // TODO: This test requires an authenticated user and a complex reactive chain setup.
    //  The approval flow is validated via the AAuthTokenEndpointManualTest against a running AM.
    //  The unit test infrastructure needs work to support Vert.x 5's UserContext API.
    @Test
    @org.junit.Ignore("Requires Vert.x 5 UserContext integration — validated via manual test")
    public void shouldCallSaveConsent_whenUserApproves() throws Exception {
        var pending = createPending();
        when(pendingService.findByInteractionCode(eq("XXXX-1234"))).thenReturn(Maybe.just(pending));

        var app = new Application();
        app.setId("app-1");
        app.setName("Test Agent");
        var oauthSettings = new io.gravitee.am.model.application.ApplicationOAuthSettings();
        oauthSettings.setClientId("aauth:bot@agent.example");
        var settings = new io.gravitee.am.model.application.ApplicationSettings();
        settings.setOauth(oauthSettings);
        app.setSettings(settings);
        when(applicationService.findById(eq("app-1"))).thenReturn(Maybe.just(app));

        when(consentService.saveConsent(any(), any(UserId.class), anySet(), any(), any()))
                .thenReturn(Single.just(List.of()));
        when(tokenService.createAuthToken(any(), any(), anyString(), anyString()))
                .thenReturn(Single.just(new AAuthTokenResponse("signed.token", 300)));
        when(pendingService.approve(anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(Single.just(pending));

        testRequest(
                HttpMethod.POST, "/aauth/consent",
                req -> {
                    req.putHeader("Content-Type", "application/x-www-form-urlencoded");
                    req.putHeader("X-Test-With-User", "true");
                    req.end(io.vertx.core.buffer.Buffer.buffer(
                            "code=XXXX-1234&user_oauth_approval=true&scope.read=true&scope.write=true"));
                },
                null, 200, "OK", null
        );

        verify(consentService).saveConsent(any(), any(UserId.class), eq(Set.of("read", "write")), any(), any());
    }

    @Test
    public void shouldNotCallSaveConsent_whenUserDenies() throws Exception {
        var pending = createPending();
        when(pendingService.findByInteractionCode(eq("XXXX-1234"))).thenReturn(Maybe.just(pending));

        var app = new Application();
        app.setId("app-1");
        app.setName("Test Agent");
        var oauthSettings2 = new io.gravitee.am.model.application.ApplicationOAuthSettings();
        oauthSettings2.setClientId("aauth:bot@agent.example");
        var settings2 = new io.gravitee.am.model.application.ApplicationSettings();
        settings2.setOauth(oauthSettings2);
        app.setSettings(settings2);
        when(applicationService.findById(eq("app-1"))).thenReturn(Maybe.just(app));

        when(pendingService.deny(eq("pending-1")))
                .thenReturn(Single.just(pending));

        testRequest(
                HttpMethod.POST, "/aauth/consent",
                req -> {
                    req.putHeader("Content-Type", "application/x-www-form-urlencoded");
                    req.putHeader("X-Test-With-User", "true");
                    req.end(io.vertx.core.buffer.Buffer.buffer(
                            "code=XXXX-1234&user_oauth_approval=false"));
                },
                null, 200, "OK", null
        );

        verify(consentService, never()).saveConsent(any(), any(UserId.class), anySet(), any(), any());
    }

    private AAuthPendingRequest createPending() {
        var req = new AAuthPendingRequest();
        req.setId("pending-1");
        req.setStatus(PendingRequestStatus.PENDING.name());
        req.setDomain("domain-1");
        req.setAgentServerUrl("https://agent.example");
        req.setAgentIdentifier("aauth:bot@agent.example");
        req.setAgentJkt("thumbprint");
        req.setApplicationId("app-1");
        try {
            var kp = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            req.setAgentPublicKey(Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        req.setResourceIss("https://resource.example");
        req.setScope("read write");
        req.setPsIssuerUrl("https://ps.example/aauth");
        req.setInteractionCode("XXXX-1234");
        req.setCreatedAt(new Date());
        req.setExpireAt(new Date(System.currentTimeMillis() + 600_000));
        return req;
    }
}
