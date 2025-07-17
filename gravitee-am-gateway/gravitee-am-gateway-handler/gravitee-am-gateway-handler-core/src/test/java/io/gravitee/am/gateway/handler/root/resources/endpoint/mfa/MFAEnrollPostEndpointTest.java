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

import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.gateway.handler.root.resources.handler.FinalRedirectLocationHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.error.ErrorHandler;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.MfaEnrollType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Session;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.vertx.core.http.HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MFAEnrollPostEndpointTest extends RxWebTestBase {

    private static final String REQUEST_PATH = "/mfa/enroll";

    @Mock
    private Domain domain;

    @Mock
    private FactorManager factorManager;
    @Mock
    private UserService userService;
    private MFAEnrollPostEndpoint mfaEnrollEndpoint;
    @Mock
    private TemplateEngine engine;
    @Mock
    private RuleEngine ruleEngine;
    private LocalSessionStore localSessionStore;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mfaEnrollEndpoint = new MFAEnrollPostEndpoint(factorManager, userService);
        localSessionStore = LocalSessionStore.create(vertx);

        localSessionStore = LocalSessionStore.create(vertx);
        router.route()
                .handler(SessionHandler.create(localSessionStore))
                .handler(BodyHandler.create());
    }



    @Test
    public void shouldNotSaveEnrollment_noFactorId() throws Exception {
        router.post(REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.next();
                })
                .handler(mfaEnrollEndpoint);

        testRequest(HttpMethod.POST,
                REQUEST_PATH,
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
                    req.write(Buffer.buffer("user_mfa_enrollment=true"));
                },
                null,
                400,
                "Bad Request", null);
    }

    @Test
    public void shouldNotSaveEnrollment_invalidFactorId() throws Exception {
        router.post(REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    Client client = new Client();
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(getApplicationFactorSettings("factor-id")));
                    client.setFactorSettings(factorSettings);
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
                .handler(mfaEnrollEndpoint);

        testRequest(HttpMethod.POST,
                REQUEST_PATH,
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
                    req.write(Buffer.buffer("user_mfa_enrollment=true&factorId=unknown"));
                },
                null,
                400,
                "Bad Request", null);
    }

    @Test
    public void shouldNotSaveEnrollment_invalidCheckSecurityFactor() throws Exception {
        router.post(REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    Client client = new Client();
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(getApplicationFactorSettings("factor-id")));
                    client.setFactorSettings(factorSettings);
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
                .handler(mfaEnrollEndpoint)
                .failureHandler(new MFAEnrollFailureHandler());


        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.checkSecurityFactor(any())).thenReturn(false);
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factor-id");
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.get(any())).thenReturn(factorProvider);
        when(factorManager.getFactor(any())).thenReturn(factor);

        testRequest(
                HttpMethod.POST,
                REQUEST_PATH,
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer
                            .appendString(ConstantKeys.MFA_ENROLLMENT_FACTOR_ID+"=factor-id")
                            .appendString("&")
                            .appendString(ConstantKeys.USER_MFA_ENROLLMENT+"="+true)
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_SHARED_SECRET+"="+UUID.randomUUID())
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_PHONE+"=%2B33691766742");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/mfa/enroll"));
                    assertTrue(location.contains("mfa_enroll_failed"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldSaveEnrollment_nominalCase() throws Exception {
        router.post(REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    Client client = new Client();
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(getApplicationFactorSettings("factor-id")));
                    client.setFactorSettings(factorSettings);
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
                .handler(mfaEnrollEndpoint)
                .handler(new FinalRedirectLocationHandler());


        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.checkSecurityFactor(any())).thenReturn(true);
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factor-id");
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.get(any())).thenReturn(factorProvider);
        when(factorManager.getFactor(any())).thenReturn(factor);

        testRequest(HttpMethod.POST,
                REQUEST_PATH,
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
                    req.write(Buffer.buffer("user_mfa_enrollment=true&factorId=factor-id"));
                },
                res -> {
                    String location = res.getHeader("Location");
                    Assert.assertTrue(location.endsWith("/oauth/authorize"));
                },
                302,
                "Found", null);
    }

    @Test
    public void shouldSaveEnrollment_nominalCase_extensionPhoneNumber() throws Exception {
        router.post(REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    Client client = new Client();
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(getApplicationFactorSettings("factor-id")));
                    client.setFactorSettings(factorSettings);
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
                .handler(mfaEnrollEndpoint)
                .handler(new FinalRedirectLocationHandler());


        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.checkSecurityFactor(any())).thenReturn(true);
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factor-id");
        when(factor.getFactorType()).thenReturn(FactorType.CALL);
        when(factorManager.get(any())).thenReturn(factorProvider);
        when(factorManager.getFactor(any())).thenReturn(factor);

        testRequest(HttpMethod.POST,
                REQUEST_PATH,
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
                    req.write(Buffer.buffer("user_mfa_enrollment=true&factorId=factor-id&extensionPhoneNumber=1234"));
                },
                res -> {
                    String location = res.getHeader("Location");
                    Assert.assertTrue(location.endsWith("/oauth/authorize"));

                    // check if extension phone number has been set in session
                    String sessionId = res.cookies().get(0).split("=")[1].split(";")[0];
                    Session session = localSessionStore.getDelegate().get(sessionId).result();
                    Map<String, Object> data = session.data();
                    Assert.assertNotNull(data.get(ConstantKeys.ENROLLED_FACTOR_EXTENSION_PHONE_NUMBER));
                    Assert.assertEquals("1234", data.get(ConstantKeys.ENROLLED_FACTOR_EXTENSION_PHONE_NUMBER));
                },
                302,
                "Found", null);
    }

    @Test
    public void shouldAcceptEnrollment_IgnoreRecoveryCode() throws Exception {
        final var RECOVERY_FACTOR_ID = UUID.randomUUID().toString();
        final var ENROLL_FACTOR_ID = UUID.randomUUID().toString();
        final var EMAIL_ADDR = "fake@acme.com";

        Factor recoveryFactor = new Factor();
        recoveryFactor.setId(RECOVERY_FACTOR_ID);
        recoveryFactor.setFactorType(FactorType.RECOVERY_CODE);

        Factor emailFactor = new Factor();
        emailFactor.setId(ENROLL_FACTOR_ID);
        emailFactor.setFactorType(FactorType.EMAIL);

        when(factorManager.getFactor(RECOVERY_FACTOR_ID)).thenReturn(recoveryFactor);
        when(factorManager.getFactor(ENROLL_FACTOR_ID)).thenReturn(emailFactor);
        FactorProvider provider = mock(FactorProvider.class);
        when(provider.checkSecurityFactor(any())).thenReturn(true);
        when(factorManager.get(ENROLL_FACTOR_ID)).thenReturn(provider);

        router.route(HttpMethod.POST, REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    user.setId("userId");
                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
                    enrolledFactor.setFactorId(RECOVERY_FACTOR_ID);
                    enrolledFactor.setStatus(FactorStatus.ACTIVATED);
                    enrolledFactor.setSecurity(enrolledFactorSecurity);
                    user.setFactors(Collections.singletonList(enrolledFactor));

                    Client client = new Client();
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(getApplicationFactorSettings(ENROLL_FACTOR_ID), getApplicationFactorSettings(RECOVERY_FACTOR_ID)));
                    client.setFactorSettings(factorSettings);
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.next();
                })
                .handler(new MFAEnrollPostEndpoint(factorManager, userService))
                .handler(new FinalRedirectLocationHandler())
                .failureHandler(new ErrorHandler(RootProvider.PATH_ERROR));

        testRequest(
                HttpMethod.POST,
                REQUEST_PATH,
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer
                            .appendString("factorId="+ENROLL_FACTOR_ID)
                            .appendString("&")
                            .appendString(ConstantKeys.USER_MFA_ENROLLMENT+"="+true)
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_SHARED_SECRET+"="+UUID.randomUUID())
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_EMAIL+"="+EMAIL_ADDR);
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/authorize"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    private static ApplicationFactorSettings getApplicationFactorSettings(String ENROLL_FACTOR_ID) {
        ApplicationFactorSettings applicationFactorSettings = new ApplicationFactorSettings();
        applicationFactorSettings.setId(ENROLL_FACTOR_ID);
        return applicationFactorSettings;
    }

    @Test
    public void shouldAcceptEnrollment_FactorIdNotActivated() throws Exception {
        final var ENROLL_FACTOR_ID = UUID.randomUUID().toString();
        final var EMAIL_ADDR = "fake@acme.com";

        Factor emailFactor = new Factor();
        emailFactor.setId(ENROLL_FACTOR_ID);
        emailFactor.setFactorType(FactorType.EMAIL);

        when(factorManager.getFactor(ENROLL_FACTOR_ID)).thenReturn(emailFactor);
        FactorProvider provider = mock(FactorProvider.class);
        when(provider.checkSecurityFactor(any())).thenReturn(true);
        when(factorManager.get(ENROLL_FACTOR_ID)).thenReturn(provider);

        router.route(HttpMethod.POST, REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    user.setId("userId");
                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
                    enrolledFactor.setFactorId(ENROLL_FACTOR_ID);
                    enrolledFactor.setStatus(FactorStatus.PENDING_ACTIVATION);
                    enrolledFactor.setSecurity(enrolledFactorSecurity);
                    user.setFactors(Collections.singletonList(enrolledFactor));

                    Client client = new Client();
                    ApplicationFactorSettings applicationFactorSettings = getApplicationFactorSettings(ENROLL_FACTOR_ID);
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(applicationFactorSettings));
                    client.setFactorSettings(factorSettings);
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.next();
                })
                .handler(new MFAEnrollPostEndpoint(factorManager, userService))
                .handler(new FinalRedirectLocationHandler())
                .failureHandler(new ErrorHandler(RootProvider.PATH_ERROR));

        testRequest(
                HttpMethod.POST,
                REQUEST_PATH,
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer
                            .appendString("factorId="+ENROLL_FACTOR_ID)
                            .appendString("&")
                            .appendString(ConstantKeys.USER_MFA_ENROLLMENT+"="+true)
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_SHARED_SECRET+"="+UUID.randomUUID())
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_EMAIL+"="+EMAIL_ADDR);
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/authorize"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRejectEnrollment_FactorIdAlreadyEnrolled() throws Exception {
        final var ENROLL_FACTOR_ID = UUID.randomUUID().toString();
        final var EMAIL_ADDR = "fake@acme.com";

        Factor emailFactor = new Factor();
        emailFactor.setId(ENROLL_FACTOR_ID);
        emailFactor.setFactorType(FactorType.EMAIL);

        when(factorManager.getFactor(ENROLL_FACTOR_ID)).thenReturn(emailFactor);
        when(factorManager.get(ENROLL_FACTOR_ID)).thenReturn(mock(FactorProvider.class));

        router.route(HttpMethod.POST, REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    user.setId("userId");
                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
                    enrolledFactor.setFactorId(ENROLL_FACTOR_ID);
                    enrolledFactor.setStatus(FactorStatus.ACTIVATED);
                    enrolledFactor.setSecurity(enrolledFactorSecurity);
                    user.setFactors(Collections.singletonList(enrolledFactor));

                    Client client = new Client();
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(getApplicationFactorSettings(ENROLL_FACTOR_ID)));
                    client.setFactorSettings(factorSettings);
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.next();
                })
                .handler(new MFAEnrollPostEndpoint(factorManager, userService))
                .failureHandler(new MFAEnrollFailureHandler());

        testRequest(
                HttpMethod.POST,
                REQUEST_PATH,
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer
                            .appendString("factorId="+ENROLL_FACTOR_ID)
                            .appendString("&")
                            .appendString(ConstantKeys.USER_MFA_ENROLLMENT+"="+true)
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_SHARED_SECRET+"="+UUID.randomUUID())
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_EMAIL+"="+EMAIL_ADDR);
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/mfa/enroll"));
                    assertTrue(location.contains("factor+already+enrolled"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRejectEnrollment_UserAlreadyEnrolledAFactor() throws Exception {
        final var USER_FACTOR_ID = UUID.randomUUID().toString();
        final var ENROLL_FACTOR_ID = UUID.randomUUID().toString();
        final var EMAIL_ADDR = "fake@acme.com";

        Factor emailFactor = new Factor();
        emailFactor.setId(ENROLL_FACTOR_ID);
        emailFactor.setFactorType(FactorType.EMAIL);

        Factor smsFactor = new Factor();
        smsFactor.setId(USER_FACTOR_ID);
        smsFactor.setFactorType(FactorType.SMS);

        when(factorManager.getFactor(ENROLL_FACTOR_ID)).thenReturn(emailFactor);
        when(factorManager.get(ENROLL_FACTOR_ID)).thenReturn(mock(FactorProvider.class));

        when(factorManager.getFactor(USER_FACTOR_ID)).thenReturn(smsFactor);
        when(factorManager.get(USER_FACTOR_ID)).thenReturn(mock(FactorProvider.class));

        router.route(HttpMethod.POST, REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    user.setId("userId");
                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
                    enrolledFactor.setFactorId(USER_FACTOR_ID);
                    enrolledFactor.setStatus(FactorStatus.ACTIVATED);
                    enrolledFactor.setSecurity(enrolledFactorSecurity);
                    user.setFactors(Collections.singletonList(enrolledFactor));

                    Client client = new Client();
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(getApplicationFactorSettings(ENROLL_FACTOR_ID), getApplicationFactorSettings(USER_FACTOR_ID)));
                    client.setFactorSettings(factorSettings);
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.next();
                })
                .handler(new MFAEnrollPostEndpoint(factorManager, userService))
                .failureHandler(new ErrorHandler(RootProvider.PATH_ERROR));

        testRequest(
                HttpMethod.POST,
                REQUEST_PATH,
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer
                            .appendString("factorId=" + ENROLL_FACTOR_ID)
                            .appendString("&")
                            .appendString(ConstantKeys.USER_MFA_ENROLLMENT + "=" + true)
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_SHARED_SECRET + "=" + UUID.randomUUID())
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_EMAIL + "=" + EMAIL_ADDR);
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/error"));
                    assertTrue(location.contains("factor+already+enrolled"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldAcceptEnrollment_MFAForceEnrollment() throws Exception {
        final var USER_FACTOR_ID = UUID.randomUUID().toString();
        final var ENROLL_FACTOR_ID = UUID.randomUUID().toString();
        final var EMAIL_ADDR = "fake@acme.com";

        Factor emailFactor = new Factor();
        emailFactor.setId(ENROLL_FACTOR_ID);
        emailFactor.setFactorType(FactorType.EMAIL);

        Factor smsFactor = new Factor();
        smsFactor.setId(USER_FACTOR_ID);
        smsFactor.setFactorType(FactorType.SMS);

        FactorProvider provider = mock(FactorProvider.class);
        when(provider.checkSecurityFactor(any())).thenReturn(true);

        when(factorManager.getFactor(ENROLL_FACTOR_ID)).thenReturn(emailFactor);
        when(factorManager.get(ENROLL_FACTOR_ID)).thenReturn(provider);

        when(factorManager.getFactor(USER_FACTOR_ID)).thenReturn(smsFactor);
        when(factorManager.get(USER_FACTOR_ID)).thenReturn(provider);

        router.route(HttpMethod.POST, REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    user.setId("userId");
                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
                    enrolledFactor.setFactorId(USER_FACTOR_ID);
                    enrolledFactor.setStatus(FactorStatus.ACTIVATED);
                    enrolledFactor.setSecurity(enrolledFactorSecurity);
                    user.setFactors(Collections.singletonList(enrolledFactor));

                    Client client = new Client();
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(getApplicationFactorSettings(ENROLL_FACTOR_ID), getApplicationFactorSettings(USER_FACTOR_ID)));
                    client.setFactorSettings(factorSettings);
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    // force MFA enrollment
                    ctx.session().put(ConstantKeys.MFA_FORCE_ENROLLMENT, true);

                    ctx.next();
                })
                .handler(new MFAEnrollPostEndpoint(factorManager, userService))
                .handler(new FinalRedirectLocationHandler())
                .failureHandler(new ErrorHandler(RootProvider.PATH_ERROR));

        testRequest(
                HttpMethod.POST,
                REQUEST_PATH,
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer
                            .appendString("factorId="+ENROLL_FACTOR_ID)
                            .appendString("&")
                            .appendString(ConstantKeys.USER_MFA_ENROLLMENT+"="+true)
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_SHARED_SECRET+"="+UUID.randomUUID())
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_EMAIL+"="+EMAIL_ADDR);
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/authorize"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotSetSkippedTimeWhenUserCannotSkip() throws Exception {
        final var USER_FACTOR_ID = UUID.randomUUID().toString();
        final var ENROLL_FACTOR_ID = UUID.randomUUID().toString();
        final var EMAIL_ADDR = "fake@acme.com";
        final var USER_ACCEPT_ENROLL = false;

        Factor emailFactor = new Factor();
        emailFactor.setId(ENROLL_FACTOR_ID);
        emailFactor.setFactorType(FactorType.EMAIL);

        Factor smsFactor = new Factor();
        smsFactor.setId(USER_FACTOR_ID);
        smsFactor.setFactorType(FactorType.SMS);

        router.route(HttpMethod.POST, REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    user.setId("userId");

                    Client client = new Client();
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(getApplicationFactorSettings(ENROLL_FACTOR_ID), getApplicationFactorSettings(USER_FACTOR_ID)));
                    client.setFactorSettings(factorSettings);

                    var mfa = new MFASettings();
                    var enroll = new EnrollSettings();
                    enroll.setActive(true);
                    enroll.setForceEnrollment(true);
                    enroll.setType(MfaEnrollType.CONDITIONAL);
                    mfa.setEnroll(enroll);
                    client.setMfaSettings(mfa);

                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.session().put(ConstantKeys.MFA_FORCE_ENROLLMENT, true);
                    ctx.session().put(ConstantKeys.MFA_ENROLL_CONDITIONAL_SKIPPED_KEY, false);

                    ctx.next();
                })
                .handler(new MFAEnrollPostEndpoint(factorManager, userService))
                .handler(new FinalRedirectLocationHandler())
                .failureHandler(new ErrorHandler(RootProvider.PATH_ERROR));

        testRequest(
                HttpMethod.POST,
                REQUEST_PATH,
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer
                            .appendString("factorId=" + ENROLL_FACTOR_ID)
                            .appendString("&")
                            .appendString(ConstantKeys.USER_MFA_ENROLLMENT + "=" + USER_ACCEPT_ENROLL)
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_SHARED_SECRET + "=" + UUID.randomUUID())
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_EMAIL + "=" + EMAIL_ADDR);
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/authorize"));
                },
                HttpStatusCode.FOUND_302, "Found", null);

        verify(userService, never()).setMfaEnrollmentSkippedTime(any(), any());
    }

    @Test
    public void shouldSetSkippedTimeWhenUserSkipped() throws Exception {
        final var ENROLL_FACTOR_ID = UUID.randomUUID().toString();
        final var EMAIL_ADDR = "fake@acme.com";
        final var USER_ACCEPT_ENROLL = false;

        when(userService.setMfaEnrollmentSkippedTime(any(), any())).thenReturn(Completable.complete());

        router.route(HttpMethod.POST, REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    user.setId("userId");

                    Client client = new Client();

                    var mfa = new MFASettings();
                    var enroll = new EnrollSettings();
                    enroll.setActive(true);
                    enroll.setForceEnrollment(true);
                    enroll.setType(MfaEnrollType.CONDITIONAL);
                    mfa.setEnroll(enroll);
                    client.setMfaSettings(mfa);

                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.session().put(ConstantKeys.MFA_FORCE_ENROLLMENT, true);
                    ctx.session().put(ConstantKeys.MFA_ENROLL_CONDITIONAL_SKIPPED_KEY, true);

                    ctx.next();
                })
                .handler(new MFAEnrollPostEndpoint(factorManager, userService))
                .handler(new FinalRedirectLocationHandler())
                .failureHandler(new ErrorHandler(RootProvider.PATH_ERROR));

        testRequest(
                HttpMethod.POST,
                REQUEST_PATH,
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer
                            .appendString("factorId=" + ENROLL_FACTOR_ID)
                            .appendString("&")
                            .appendString(ConstantKeys.USER_MFA_ENROLLMENT + "=" + USER_ACCEPT_ENROLL)
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_SHARED_SECRET + "=" + UUID.randomUUID())
                            .appendString("&")
                            .appendString(ConstantKeys.MFA_ENROLLMENT_EMAIL + "=" + EMAIL_ADDR);
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/authorize"));
                },
                HttpStatusCode.FOUND_302, "Found", null);

        verify(userService, times(1)).setMfaEnrollmentSkippedTime(any(), any());
    }
}
