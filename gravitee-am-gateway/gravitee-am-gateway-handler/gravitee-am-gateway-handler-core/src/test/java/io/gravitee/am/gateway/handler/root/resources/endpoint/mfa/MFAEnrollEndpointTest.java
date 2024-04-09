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
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.gateway.handler.root.resources.handler.error.ErrorHandler;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import static io.vertx.core.http.HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MFAEnrollEndpointTest extends RxWebTestBase {

    private static final String REQUEST_PATH = "/mfa/enroll";

    @Mock
    private Domain domain;

    @Mock
    private ThymeleafTemplateEngine templateEngine;

    @Mock
    private FactorManager factorManager;
    @Mock
    private UserService userService;
    @Mock
    private ApplicationContext applicationContext;
    private MFAEnrollEndpoint mfaEnrollEndpoint;
    @Mock
    private TemplateEngine engine;
    private LocalSessionStore localSessionStore;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mfaEnrollEndpoint = new MFAEnrollEndpoint(factorManager, templateEngine, userService, domain, applicationContext);
        localSessionStore = LocalSessionStore.create(vertx);

        router.route()
                .handler(SessionHandler.create(localSessionStore))
                .handler(BodyHandler.create());
    }

    @Test
    public void shouldNotRenderPage_noUser() throws Exception {
        router.route(REQUEST_PATH)
                .handler(mfaEnrollEndpoint)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.GET,
                REQUEST_PATH,
                401,
                "Unauthorized");
    }

    @Test
    public void shouldRenderPage_nominalCase() throws Exception {
        when(templateEngine.render(anyMap(), any())).thenReturn(Single.just(Buffer.buffer()));

        router.route(REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    Client client = new Client();
                    client.setFactors(Collections.singleton("factor-id"));
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
                .handler(mfaEnrollEndpoint)
                .handler(rc -> rc.response().end());

        Enrollment enrollment = mock(Enrollment.class);
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.enroll(any(FactorContext.class))).thenReturn(Single.just(enrollment));
        Factor factor = mock(Factor.class);
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.get(any())).thenReturn(factorProvider);
        when(factorManager.getFactor(any())).thenReturn(factor);

        testRequest(HttpMethod.GET,
                REQUEST_PATH,
                200,
                "OK");
    }

    @Test
    public void shouldNotRenderPageWhenFactorEnrolled() throws Exception {
        final var ENROLL_FACTOR_ID = UUID.randomUUID().toString();
        router.route(REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();

                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
                    enrolledFactor.setFactorId(ENROLL_FACTOR_ID);
                    enrolledFactor.setStatus(FactorStatus.ACTIVATED);
                    enrolledFactor.setSecurity(enrolledFactorSecurity);
                    user.setFactors(Collections.singletonList(enrolledFactor));

                    Client client = new Client();
                    client.setFactors(Collections.singleton(ENROLL_FACTOR_ID));
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
                .handler(mfaEnrollEndpoint)
                .handler(rc -> rc.response().end());

        Factor factor = mock(Factor.class);
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.getFactor(any())).thenReturn(factor);

        testRequest(HttpMethod.GET,
                REQUEST_PATH,
                302,
                "Found");
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
                    client.setFactors(Collections.singleton("factor-id"));
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
                    client.setFactors(Collections.singleton("factor-id"));
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
                .handler(mfaEnrollEndpoint);


        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.checkSecurityFactor(any())).thenReturn(false);
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
                null,
                400,
                "Bad Request", null);
    }

    @Test
    public void shouldSaveEnrollment_nominalCase() throws Exception {
        router.post(REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    Client client = new Client();
                    client.setFactors(Collections.singleton("factor-id"));
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
                .handler(mfaEnrollEndpoint);


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
                    client.setFactors(Set.of(ENROLL_FACTOR_ID, RECOVERY_FACTOR_ID));
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.next();
                })
                .handler(new MFAEnrollEndpoint(factorManager, engine, userService, domain, applicationContext))
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
                    client.setFactors(Collections.singleton(ENROLL_FACTOR_ID));
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.next();
                })
                .handler(new MFAEnrollEndpoint(factorManager, engine, userService, domain, applicationContext))
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
                    client.setFactors(Collections.singleton(ENROLL_FACTOR_ID));
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.next();
                })
                .handler(new MFAEnrollEndpoint(factorManager, engine, userService, domain, applicationContext))
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
                    assertTrue(location.contains("/error"));
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
                    client.setFactors(Set.of(ENROLL_FACTOR_ID, USER_FACTOR_ID));
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.next();
                })
                .handler(new MFAEnrollEndpoint(factorManager, engine, userService, domain, applicationContext))
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
}
