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
package io.gravitee.am.gateway.handler.root.resources.endpoint.mfa;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.*;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.util.Collections;

import static io.vertx.core.http.HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MFAChallengeEndpointTest extends RxWebTestBase {

    @Mock
    private TemplateEngine templateEngine;
    @Mock
    private FactorManager factorManager;
    @Mock
    private UserService userService;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private DeviceService deviceService;
    @Mock
    private Domain domain;
    @Mock
    private CredentialService credentialService;
    @Mock
    private FactorService factorService;
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private VerifyAttemptService verifyAttemptService;
    @Mock
    private EmailService emailService;
    @Mock
    private AuthenticationFlowContextService authenticationFlowContextService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        MFAChallengeEndpoint mfaChallengeEndpoint =
                new MFAChallengeEndpoint(factorManager, userService, templateEngine, deviceService, applicationContext,
                        domain, credentialService, factorService, rateLimiterService, verifyAttemptService, emailService);

        router.route("/mfa/challenge")
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(BodyHandler.create())
                .handler(mfaChallengeEndpoint)
                .failureHandler(new MFAChallengeFailureHandler(authenticationFlowContextService));
    }

    @Test
    public void shouldNotVerifyCode_noUser() throws Exception {
        testRequest(HttpMethod.POST,
                "/mfa/challenge",
                null,
                res -> {
                    String location = res.getHeader("Location");
                    Assert.assertTrue(location.contains("/login?error=mfa_challenge_failed"));
                },
                302,
                "Found", null);
    }

    @Test
    public void shouldNotVerifyCode_noCode() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        when(authenticationFlowContextService.clearContext(any())).thenReturn(Completable.complete());

        testRequest(HttpMethod.POST,
                "/mfa/challenge",
                null,
                res -> {
                    String location = res.getHeader("Location");
                    Assert.assertTrue(location.contains("/login?error=mfa_challenge_failed"));
                },
                302,
                "Found", null);
    }

    @Test
    public void shouldNotVerifyCode_noFactorId() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        when(authenticationFlowContextService.clearContext(any())).thenReturn(Completable.complete());

        testRequest(HttpMethod.POST,
                "/mfa/challenge",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
                    req.write(Buffer.buffer("code=123456"));
                },
                res -> {
                    String location = res.getHeader("Location");
                    Assert.assertTrue(location.contains("/login?error=mfa_challenge_failed"));
                },
                302,
                "Found", null);
    }

    @Test
    public void shouldVerifyCode_nominalCase() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            client.setFactors(Collections.singleton("factor"));
            User endUser = new User();
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor");
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factor");
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.verify(any())).thenReturn(Completable.complete());
        when(factorManager.getFactor("factor")).thenReturn(factor);
        when(factorManager.get("factor")).thenReturn(factorProvider);
        when(verifyAttemptService.checkVerifyAttempt(any(), any(), any(), any())).thenReturn(Maybe.empty());

        testRequest(HttpMethod.POST,
                "/mfa/challenge",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
                    req.write(Buffer.buffer("code=123456&factorId=factor"));
                },
                res -> {
                    String location = res.getHeader("Location");
                    Assert.assertTrue(location.endsWith("/oauth/authorize"));
                },
                302,
                "Found", null);
    }
}
