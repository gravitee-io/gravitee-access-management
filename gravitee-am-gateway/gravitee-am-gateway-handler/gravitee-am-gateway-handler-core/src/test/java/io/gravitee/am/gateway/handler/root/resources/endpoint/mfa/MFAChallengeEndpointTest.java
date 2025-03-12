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

import io.gravitee.am.common.exception.mfa.SendChallengeException;
import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.gateway.handler.common.service.DeviceGatewayService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.VerifyAttempt;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.gateway.handler.common.service.mfa.RateLimiterService;
import io.gravitee.am.gateway.handler.common.service.mfa.VerifyAttemptService;
import io.gravitee.am.service.DomainDataPlane;
import io.gravitee.am.service.exception.MFAValidationAttemptException;
import io.gravitee.am.service.reporter.builder.gateway.VerifyAttemptAuditBuilder;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Session;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.vertx.core.http.HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    private DeviceGatewayService deviceService;
    @Mock
    private Domain domain;
    @Mock
    private DomainDataPlane domainDataPlane;
    @Mock
    private CredentialGatewayService credentialService;
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private VerifyAttemptService verifyAttemptService;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;
    @Mock
    private AuthenticationFlowContextService authenticationFlowContextService;

    private LocalSessionStore localSessionStore;
    private MFAChallengeEndpoint mfaChallengeEndpoint;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        localSessionStore = LocalSessionStore.create(vertx);
        mfaChallengeEndpoint =
                new MFAChallengeEndpoint(factorManager, userService, templateEngine, deviceService, applicationContext,
                        domainDataPlane, credentialService, rateLimiterService, verifyAttemptService, emailService, auditService);

        router.route("/mfa/challenge")
                .handler(SessionHandler.create(localSessionStore))
                .handler(BodyHandler.create())
                .handler(mfaChallengeEndpoint)
                .failureHandler(new MFAChallengeFailureHandler(authenticationFlowContextService));
        when(domain.getId()).thenReturn("id");
        when(domainDataPlane.getDomain()).thenReturn(domain);
    }

    @Test
    public void shouldVerify_fido2Factor() throws Exception {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.verify(any())).thenReturn(Completable.complete());
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factorId");
        when(factor.is(FactorType.FIDO2)).thenReturn(true);
        when(factorManager.get("factorId")).thenReturn(factorProvider);
        when(factorManager.getFactor("factorId")).thenReturn(factor);
        when(credentialService.update(any(), any(), any())).thenReturn(Single.just(new Credential()));
        when(verifyAttemptService.checkVerifyAttempt(any(), any(), any(), any())).thenReturn(Maybe.empty());
        when(userService.upsertFactor(any(), any(), any())).thenReturn(Single.just(mock(User.class)));

        router.route(HttpMethod.POST, "/mfa/challenge")
                .order(-1)
                .handler(ctx -> {
                    Client client = new Client();
                    ApplicationFactorSettings applicationFactorSettings = new ApplicationFactorSettings();
                    applicationFactorSettings.setId("factorId");
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(applicationFactorSettings));
                    client.setFactorSettings(factorSettings);
                    User endUser = new User();
                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    enrolledFactor.setFactorId("factorId");
                    endUser.setFactors(Collections.singletonList(enrolledFactor));
                    ctx.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                });

        testRequest(
                HttpMethod.POST,
                "/mfa/challenge",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("code={\"id\":\"credentialId\"}&factorId=factorId");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/oauth/authorize"));
                    // check if credentialId has been set in session
                    String sessionId = resp.cookies().get(0).split("=")[1].split(";")[0];
                    Session session = localSessionStore.getDelegate().get(sessionId).result();
                    Map<String, Object> data = session.data();
                    Assert.assertNotNull(data.get(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY));
                    Assert.assertEquals("credentialId", data.get(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY));
                },
                HttpStatusCode.FOUND_302, "Found", null);

        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldVerify_withExtensionPhoneNumber() throws Exception {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.verify(any())).thenReturn(Completable.complete());
        ArgumentCaptor<EnrolledFactor> enrolledFactorArgumentCaptor = ArgumentCaptor.forClass(EnrolledFactor.class);
        when(factorProvider.changeVariableFactorSecurity(enrolledFactorArgumentCaptor.capture())).thenReturn(Single.just(new EnrolledFactor()));
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factorId");
        when(factor.getFactorType()).thenReturn(FactorType.CALL);
        when(factorManager.get("factorId")).thenReturn(factorProvider);
        when(factorManager.getFactor("factorId")).thenReturn(factor);
        when(verifyAttemptService.checkVerifyAttempt(any(), any(), any(), any())).thenReturn(Maybe.empty());
        when(userService.upsertFactor(any(), any(), any())).thenReturn(Single.just(mock(User.class)));

        router.route("/mfa/challenge/for/phoneExt")
                .handler(SessionHandler.create(localSessionStore))
                .handler(BodyHandler.create())
                .handler(routingContext -> {
                    Client client = new Client();
                    client.setFactors(Collections.singleton("factorId"));
                    routingContext.session().put(ConstantKeys.ENROLLED_FACTOR_ID_KEY, "factorId");
                    routingContext.session().put(ConstantKeys.ENROLLED_FACTOR_PHONE_NUMBER, "9999999");
                    routingContext.session().put(ConstantKeys.ENROLLED_FACTOR_EXTENSION_PHONE_NUMBER, "1234");
                    routingContext.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new User())));
                    routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    routingContext.next();
                })
                .handler(mfaChallengeEndpoint)
                .failureHandler(new MFAChallengeFailureHandler(authenticationFlowContextService));

        testRequest(
                HttpMethod.POST,
                "/mfa/challenge/for/phoneExt",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("code=123456&factorId=factorId");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/oauth/authorize"));

                    // check if extension phone number has been stored
                    EnrolledFactor enrolledFactor = enrolledFactorArgumentCaptor.getValue();
                    assertNotNull(enrolledFactor);
                    assertNotNull(enrolledFactor.getChannel());
                    assertNotNull(enrolledFactor.getChannel().getAdditionalData());
                    assertEquals("1234", enrolledFactor.getChannel().getAdditionalData().get(ConstantKeys.MFA_ENROLLMENT_EXTENSION_PHONE_NUMBER));
                },
                HttpStatusCode.FOUND_302, "Found", null);
   }

    @Test
    public void shouldSendCode_withEmail_tidUsedAsMovingFactor() throws Exception {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.needChallengeSending()).thenReturn(true);
        when(factorProvider.useVariableFactorSecurity(any())).thenReturn(true);
        when(factorProvider.sendChallenge(any())).thenReturn(Completable.complete());
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factorId");
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.get("factorId")).thenReturn(factorProvider);
        when(factorManager.getFactor("factorId")).thenReturn(factor);
        when(templateEngine.render(any(Map.class), any())).thenReturn(Single.just(Buffer.buffer()));

        SpyRoutingContext spyRoutingContext = new SpyRoutingContext();
        spyRoutingContext.setMethod(HttpMethod.GET);
        Client client = new Client();
        client.setFactors(Collections.singleton("factorId"));
        spyRoutingContext.session().put(ConstantKeys.ENROLLED_FACTOR_ID_KEY, "factorId");
        spyRoutingContext.session().put(ConstantKeys.ENROLLED_FACTOR_EMAIL_ADDRESS, "user01@acme.fr");
        spyRoutingContext.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new User())));
        spyRoutingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        spyRoutingContext.put(ConstantKeys.TRANSACTION_ID_KEY, UUID.randomUUID().toString());

        mfaChallengeEndpoint.handle(spyRoutingContext);

        int attempt = 20;
        while (!spyRoutingContext.ended() && attempt > 0) {
            Thread.sleep(1000);
            --attempt;
        }

        assertTrue(spyRoutingContext.session().data().containsKey(MFAChallengeEndpoint.PREVIOUS_TRANSACTION_ID_KEY));
        assertNull(spyRoutingContext.response().headers().get("location"));
        assertEquals(MediaType.TEXT_HTML, spyRoutingContext.response().headers().get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void shouldSendSameCode_withEmail_afterErrorValidation() throws Exception {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.needChallengeSending()).thenReturn(true);
        when(factorProvider.useVariableFactorSecurity(any())).thenReturn(true);
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factorId");
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.get("factorId")).thenReturn(factorProvider);
        when(factorManager.getFactor("factorId")).thenReturn(factor);
        when(templateEngine.render(any(Map.class), any())).thenReturn(Single.just(Buffer.buffer()));

        SpyRoutingContext spyRoutingContext = new SpyRoutingContext();
        spyRoutingContext.setMethod(HttpMethod.GET);
        Client client = new Client();
        client.setFactors(Collections.singleton("factorId"));
        spyRoutingContext.session().put(ConstantKeys.ENROLLED_FACTOR_ID_KEY, "factorId");
        spyRoutingContext.session().put(ConstantKeys.ENROLLED_FACTOR_EMAIL_ADDRESS, "user01@acme.fr");
        spyRoutingContext.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new User())));
        spyRoutingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        spyRoutingContext.put(ConstantKeys.TRANSACTION_ID_KEY, UUID.randomUUID().toString());
        String previousTid = UUID.randomUUID().toString();
        spyRoutingContext.session().put(MFAChallengeEndpoint.PREVIOUS_TRANSACTION_ID_KEY, previousTid);
        spyRoutingContext.putParam(ConstantKeys.ERROR_PARAM_KEY, "dummy_error");

        mfaChallengeEndpoint.handle(spyRoutingContext);

        int attempt = 20;
        while (!spyRoutingContext.ended() && attempt > 0) {
            Thread.sleep(1000);
            --attempt;
        }

        assertTrue(spyRoutingContext.session().data().containsKey(MFAChallengeEndpoint.PREVIOUS_TRANSACTION_ID_KEY));
        assertEquals(previousTid, spyRoutingContext.session().data().get(MFAChallengeEndpoint.PREVIOUS_TRANSACTION_ID_KEY));
        assertNull(spyRoutingContext.response().headers().get("location"));
        assertEquals(MediaType.TEXT_HTML, spyRoutingContext.response().headers().get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void shouldSendSameCode_withEmail_afterRateLimitError() throws Exception {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.needChallengeSending()).thenReturn(true);
        when(factorProvider.useVariableFactorSecurity(any())).thenReturn(true);
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factorId");
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.get("factorId")).thenReturn(factorProvider);
        when(factorManager.getFactor("factorId")).thenReturn(factor);

        SpyRoutingContext spyRoutingContext = new SpyRoutingContext();
        spyRoutingContext.setMethod(HttpMethod.GET);
        Client client = new Client();
        client.setFactors(Collections.singleton("factorId"));
        spyRoutingContext.session().put(ConstantKeys.ENROLLED_FACTOR_ID_KEY, "factorId");
        spyRoutingContext.session().put(ConstantKeys.ENROLLED_FACTOR_EMAIL_ADDRESS, "user01@acme.fr");
        spyRoutingContext.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new User())));
        spyRoutingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        spyRoutingContext.put(ConstantKeys.TRANSACTION_ID_KEY, UUID.randomUUID().toString());
        String previousTid = UUID.randomUUID().toString();
        spyRoutingContext.session().put(MFAChallengeEndpoint.PREVIOUS_TRANSACTION_ID_KEY, previousTid);

        when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        when(rateLimiterService.tryConsume(any(), any(), any(), any())).thenReturn(Single.just(false));

        mfaChallengeEndpoint.handle(spyRoutingContext);

        int attempt = 20;
        while (!spyRoutingContext.ended() && attempt > 0) {
            Thread.sleep(1000);
            --attempt;
        }

        assertTrue(spyRoutingContext.session().data().containsKey(MFAChallengeEndpoint.PREVIOUS_TRANSACTION_ID_KEY));
        assertEquals(previousTid, spyRoutingContext.session().data().get(MFAChallengeEndpoint.PREVIOUS_TRANSACTION_ID_KEY));
        assertTrue(spyRoutingContext.response().headers().get("location").contains("request_limit_error"));
    }

    @Test
    public void shouldSendAnotherCode_withEmail_ifRateLimiteSuccessful() throws Exception {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.needChallengeSending()).thenReturn(true);
        when(factorProvider.useVariableFactorSecurity(any())).thenReturn(true);
        when(factorProvider.sendChallenge(any())).thenReturn(Completable.complete());
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factorId");
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.get("factorId")).thenReturn(factorProvider);
        when(factorManager.getFactor("factorId")).thenReturn(factor);
        when(templateEngine.render(any(Map.class), any())).thenReturn(Single.just(Buffer.buffer()));

        SpyRoutingContext spyRoutingContext = new SpyRoutingContext();
        spyRoutingContext.setMethod(HttpMethod.GET);
        Client client = new Client();
        client.setFactors(Collections.singleton("factorId"));
        spyRoutingContext.session().put(ConstantKeys.ENROLLED_FACTOR_ID_KEY, "factorId");
        spyRoutingContext.session().put(ConstantKeys.ENROLLED_FACTOR_EMAIL_ADDRESS, "user01@acme.fr");
        spyRoutingContext.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new User())));
        spyRoutingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        spyRoutingContext.put(ConstantKeys.TRANSACTION_ID_KEY, UUID.randomUUID().toString());
        String previousTid = UUID.randomUUID().toString();
        spyRoutingContext.session().put(MFAChallengeEndpoint.PREVIOUS_TRANSACTION_ID_KEY, previousTid);

        when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        when(rateLimiterService.tryConsume(any(), any(), any(), any())).thenReturn(Single.just(true));

        mfaChallengeEndpoint.handle(spyRoutingContext);

        int attempt = 20;
        while (!spyRoutingContext.ended() && attempt > 0) {
            Thread.sleep(1000);
            --attempt;
        }

        assertTrue(spyRoutingContext.session().data().containsKey(MFAChallengeEndpoint.PREVIOUS_TRANSACTION_ID_KEY));
        Assertions.assertThat(previousTid).isNotEqualTo(spyRoutingContext.session().data().get(MFAChallengeEndpoint.PREVIOUS_TRANSACTION_ID_KEY));
        assertNull(spyRoutingContext.response().headers().get("location"));
        assertEquals(MediaType.TEXT_HTML, spyRoutingContext.response().headers().get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void shouldVerify_withEmail_tidRemovedFromSession() throws Exception {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.verify(any())).thenReturn(Completable.complete());
        ArgumentCaptor<EnrolledFactor> enrolledFactorArgumentCaptor = ArgumentCaptor.forClass(EnrolledFactor.class);
        when(factorProvider.changeVariableFactorSecurity(enrolledFactorArgumentCaptor.capture())).thenReturn(Single.just(new EnrolledFactor()));
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factorId");
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.get("factorId")).thenReturn(factorProvider);
        when(factorManager.getFactor("factorId")).thenReturn(factor);
        when(verifyAttemptService.checkVerifyAttempt(any(), any(), any(), any())).thenReturn(Maybe.empty());
        when(userService.upsertFactor(any(), any(), any())).thenReturn(Single.just(mock(User.class)));

        SpyRoutingContext spyRoutingContext = new SpyRoutingContext();
        spyRoutingContext.setMethod(HttpMethod.POST);
        Client client = new Client();
        client.setFactors(Collections.singleton("factorId"));
        spyRoutingContext.session().put(ConstantKeys.ENROLLED_FACTOR_ID_KEY, "factorId");
        spyRoutingContext.session().put(ConstantKeys.ENROLLED_FACTOR_EMAIL_ADDRESS, "user01@acme.fr");
        spyRoutingContext.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new User())));
        spyRoutingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        spyRoutingContext.put(ConstantKeys.TRANSACTION_ID_KEY, UUID.randomUUID().toString());

        spyRoutingContext.request().formAttributes().add("code", "123456");
        spyRoutingContext.request().formAttributes().add("factorId", "factorId");

        mfaChallengeEndpoint.handle(spyRoutingContext);

        int attempt = 20;
        while (!spyRoutingContext.ended() && attempt > 0) {
            Thread.sleep(1000);
            --attempt;
        }

        assertFalse(spyRoutingContext.session().data().containsKey(MFAChallengeEndpoint.PREVIOUS_TRANSACTION_ID_KEY));
        String location = spyRoutingContext.response().headers().get("location");
        assertNotNull(location);
        assertTrue(location.contains("/oauth/authorize"));
    }

    @Test
    public void shouldNotVerifyCode_noUser() throws Exception {
        router.route(HttpMethod.POST, "/mfa/challenge");

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
        router.route(HttpMethod.POST, "/mfa/challenge")
                .handler(routingContext -> {
                    User endUser = new User();
                    routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    routingContext.next();
                })
                .handler(mfaChallengeEndpoint);

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
        router.route(HttpMethod.POST, "/mfa/challenge")
                .handler(routingContext -> {
                    User endUser = new User();
                    routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    routingContext.next();
                })
                .handler(mfaChallengeEndpoint);

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
        router.route(HttpMethod.POST, "/mfa/challenge")
                .order(-1)
                .handler(routingContext -> {
                    Client client = new Client();
                    ApplicationFactorSettings applicationFactorSettings = new ApplicationFactorSettings();
                    applicationFactorSettings.setId("factor");
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(applicationFactorSettings));
                    client.setFactorSettings(factorSettings);
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
        when(factorProvider.useVariableFactorSecurity(any())).thenReturn(false);
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

        verify(auditService).report(any());
        verify(userService, never()).addFactor(any(), any(), any());
    }

    @Test
    public void shouldVerifyCode_nominalCase_saveFactor() throws Exception {
        final var endUser = new User();
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("factor");
        enrolledFactor.setStatus(FactorStatus.ACTIVATED);
        endUser.setFactors(Collections.singletonList(enrolledFactor));

        router.route(HttpMethod.POST, "/mfa/challenge")
                .order(-1)
                .handler(routingContext -> {
                    Client client = new Client();
                    ApplicationFactorSettings applicationFactorSettings = new ApplicationFactorSettings();
                    applicationFactorSettings.setId("factor");
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(applicationFactorSettings));
                    client.setFactorSettings(factorSettings);
                    routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    routingContext.next();
                });

        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factor");
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.verify(any())).thenReturn(Completable.complete());
        when(factorProvider.useVariableFactorSecurity(any())).thenReturn(true);
        when(factorProvider.changeVariableFactorSecurity(any())).thenAnswer(arg -> Single.just(arg.getArguments()[0]));
        when(factorManager.getFactor("factor")).thenReturn(factor);
        when(factorManager.get("factor")).thenReturn(factorProvider);
        when(verifyAttemptService.checkVerifyAttempt(any(), any(), any(), any())).thenReturn(Maybe.empty());
        when(userService.upsertFactor(any(), any(), any())).thenAnswer(args -> Single.just(endUser));
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

        verify(auditService).report(any());
        verify(userService).upsertFactor(any(), any(), any());
    }

    @Test
    public void shouldVerifyCode_enrollementCase_saveFactor() throws Exception {
        final var endUser = new User();
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("factor");
        enrolledFactor.setStatus(FactorStatus.PENDING_ACTIVATION);
        endUser.setFactors(Collections.singletonList(enrolledFactor));

        router.route(HttpMethod.POST, "/mfa/challenge")
                .order(-1)
                .handler(routingContext -> {
                    Client client = new Client();
                    ApplicationFactorSettings applicationFactorSettings = new ApplicationFactorSettings();
                    applicationFactorSettings.setId("factor");
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(applicationFactorSettings));
                    client.setFactorSettings(factorSettings);
                    routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    routingContext.next();
                });

        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factor");
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.verify(any())).thenReturn(Completable.complete());
        when(factorProvider.changeVariableFactorSecurity(any())).thenAnswer(arg -> Single.just(arg.getArguments()[0]));
        when(factorManager.getFactor("factor")).thenReturn(factor);
        when(factorManager.get("factor")).thenReturn(factorProvider);
        when(verifyAttemptService.checkVerifyAttempt(any(), any(), any(), any())).thenReturn(Maybe.empty());
        when(userService.upsertFactor(any(), any(), any())).thenAnswer(args -> Single.just(endUser));
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

        verify(auditService).report(any());
        verify(userService).upsertFactor(any(), any(), any());
    }

    @Test
    public void shouldVerifyCode_TooManyAttempt() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            ApplicationFactorSettings applicationFactorSettings = new ApplicationFactorSettings();
            applicationFactorSettings.setId("factor");
            FactorSettings factorSettings = new FactorSettings();
            factorSettings.setApplicationFactors(List.of(applicationFactorSettings));
            client.setFactorSettings(factorSettings);
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
        when(factorManager.getFactor("factor")).thenReturn(factor);
        when(factorManager.get("factor")).thenReturn(factorProvider);
        when(verifyAttemptService.checkVerifyAttempt(any(), any(), any(), any())).thenReturn(Maybe.error(new MFAValidationAttemptException(new VerifyAttempt(), "too many attempts")));

        testRequest(HttpMethod.POST,
                "/mfa/challenge",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
                    req.write(Buffer.buffer("code=123456&factorId=factor"));
                },
                res -> {
                    String location = res.getHeader("Location");
                    Assert.assertTrue(location.contains("/mfa/challenge"));
                },
                302,
                "Found", null);

        verify(auditService).report(argThat(builder -> builder instanceof VerifyAttemptAuditBuilder));
    }

    @Test
    public void shouldRedirectToMfaChallenge_errorCouldNotSendCode() throws Exception {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.needChallengeSending()).thenReturn(true);
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factorId");
        when(factor.is(FactorType.FIDO2)).thenReturn(false);
        when(factorManager.get("factorId")).thenReturn(factorProvider);
        when(factorManager.getFactor("factorId")).thenReturn(factor);
        when(factorProvider.sendChallenge(any())).thenReturn(Completable.error(new SendChallengeException("Could not send code")));

        router.route(HttpMethod.GET, "/mfa/challenge")
                .order(-1)
                .handler(ctx -> {
                    User user = createUser();
                    Client client = new Client();
                    ApplicationFactorSettings applicationFactorSettings = new ApplicationFactorSettings();
                    applicationFactorSettings.setId("factorId");
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(applicationFactorSettings));
                    client.setFactorSettings(factorSettings);
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                });

        testRequest(
                HttpMethod.GET,
          "/mfa/challenge",
                req -> {},
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/mfa/challenge?error=mfa_challenge_failed&error_code=send_challenge_failed&error_description=Could+not+send+code"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

  @Test
  public void shouldRedirectToError_unexpectedError() throws Exception {
    router.route(HttpMethod.GET, "/mfa/challenge").order(-1)
        .handler(ctx -> {
          User user = createUser();
          Client client = new Client();
          client.setFactors(Collections.singleton("factorId"));
          ctx.setUser(new io.vertx.rxjava3.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
          ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
          ctx.next();
        });

        testRequest(
                HttpMethod.GET,
                "/mfa/challenge",
                req -> {},
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/error?error=server_error&error_description=Unexpected+error+occurred"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
  }

  @Test
  public void shouldRedirectToError_userNotPresentMFASendChallenge() throws Exception {
    router.route(HttpMethod.GET, "/mfa/challenge")
        .handler(ctx -> {
          Client client = new Client();
          client.setFactors(Collections.singleton("factorId"));
          ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
          ctx.next();
        });

        testRequest(
                HttpMethod.GET,
                "/mfa/challenge",
                req -> {},
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/login?error=mfa_challenge_failed&error_code=send_challenge_failed&error_description=MFA+Challenge+failed+for+unexpected+reason"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
  }
  private static User createUser() {
      User user = new User();
      user.setId("userId");
      createUserFactor(user);
      return user;
  }

  private static void createUserFactor(User user) {
      EnrolledFactor enrolledFactor = new EnrolledFactor();
      EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
      enrolledFactor.setFactorId("factorId");
      enrolledFactor.setSecurity(enrolledFactorSecurity);
      user.setFactors(Collections.singletonList(enrolledFactor));
  }
}
