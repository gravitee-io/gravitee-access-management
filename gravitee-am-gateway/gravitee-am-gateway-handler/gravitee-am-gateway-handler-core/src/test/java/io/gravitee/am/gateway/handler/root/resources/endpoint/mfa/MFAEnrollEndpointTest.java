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
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
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
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
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

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mfaEnrollEndpoint = new MFAEnrollEndpoint(factorManager, templateEngine, userService, domain, applicationContext);

        router.route()
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
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
}
