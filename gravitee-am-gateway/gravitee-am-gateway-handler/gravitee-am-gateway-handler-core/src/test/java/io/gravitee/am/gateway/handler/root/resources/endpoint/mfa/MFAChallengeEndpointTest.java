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

import io.gravitee.am.common.factor.FactorSecurityType;
import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.DeviceService;
import io.gravitee.am.service.FactorService;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Completable;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Session;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.handler.SessionHandler;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.Map;

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
    private FactorManager factorManager;

    @Mock
    private UserService userService;

    @Mock
    private TemplateEngine engine;

    @Mock
    private DeviceService deviceService;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private Domain domain;

    @Mock
    private CredentialService credentialService;

    @Mock
    private FactorService factorService;

    private LocalSessionStore localSessionStore;

    @Override
    public void setUp() throws Exception {
        super.setUp();


        localSessionStore = LocalSessionStore.create(vertx);
        router.route("/mfa/challenge")
                .handler(SessionHandler.create(localSessionStore))
                .handler(BodyHandler.create())
                .failureHandler(rc -> rc.response().setStatusCode(500).end());
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
        when(credentialService.update(any(), any(), any(), any())).thenReturn(Completable.complete());

        router.route(HttpMethod.POST, "/mfa/challenge")
                .handler(ctx -> {
                    User user = new User();
                    user.setId("userId");
                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
                    enrolledFactorSecurity.setType(FactorSecurityType.WEBAUTHN_CREDENTIAL);
                    enrolledFactor.setFactorId("factorId");
                    enrolledFactor.setSecurity(enrolledFactorSecurity);
                    user.setFactors(Collections.singletonList(enrolledFactor));
                    Client client = new Client();
                    client.setFactors(Collections.singleton("factorId"));
                    ctx.setUser(io.vertx.reactivex.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
                .handler(new MFAChallengeEndpoint(factorManager, userService, engine, deviceService, applicationContext, domain, credentialService, factorService));

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
    }
}
