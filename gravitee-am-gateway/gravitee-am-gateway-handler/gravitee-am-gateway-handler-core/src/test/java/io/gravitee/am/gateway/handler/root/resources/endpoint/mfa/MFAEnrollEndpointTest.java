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

import com.nimbusds.jose.JWEHeader;
import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
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
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.handler.SessionHandler;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MFAEnrollEndpointTest extends RxWebTestBase {

    @Mock
    private FactorManager factorManager;

    @Mock
    private UserService userService;

    @Mock
    private TemplateEngine engine;
    @Mock
    private Domain domain;
    private LocalSessionStore localSessionStore;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        localSessionStore = LocalSessionStore.create(vertx);
        router.route("/mfa/enroll")
                .handler(SessionHandler.create(localSessionStore))
                .handler(BodyHandler.create());
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

        router.route(HttpMethod.POST, "/mfa/enroll")
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
                    ctx.setUser(io.vertx.reactivex.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.next();
                })
                .handler(new MFAEnrollEndpoint(factorManager, engine, userService, domain))
                .failureHandler(new ErrorHandler(RootProvider.PATH_ERROR));

        testRequest(
                HttpMethod.POST,
                "/mfa/enroll",
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

        router.route(HttpMethod.POST, "/mfa/enroll")
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
                    ctx.setUser(io.vertx.reactivex.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.next();
                })
                .handler(new MFAEnrollEndpoint(factorManager, engine, userService, domain))
                .failureHandler(new ErrorHandler(RootProvider.PATH_ERROR));

        testRequest(
                HttpMethod.POST,
                "/mfa/enroll",
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

        router.route(HttpMethod.POST, "/mfa/enroll")
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
                    ctx.setUser(io.vertx.reactivex.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.next();
                })
                .handler(new MFAEnrollEndpoint(factorManager, engine, userService, domain))
                .failureHandler(new ErrorHandler(RootProvider.PATH_ERROR));

        testRequest(
                HttpMethod.POST,
                "/mfa/enroll",
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

        router.route(HttpMethod.POST, "/mfa/enroll")
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
                    ctx.setUser(io.vertx.reactivex.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

                    ctx.next();
                })
                .handler(new MFAEnrollEndpoint(factorManager, engine, userService, domain))
                .failureHandler(new ErrorHandler(RootProvider.PATH_ERROR));

        testRequest(
                HttpMethod.POST,
                "/mfa/enroll",
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
}
