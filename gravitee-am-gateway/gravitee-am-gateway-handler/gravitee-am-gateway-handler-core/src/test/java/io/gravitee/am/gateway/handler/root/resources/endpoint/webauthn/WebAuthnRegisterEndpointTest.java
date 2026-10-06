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
package io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DomainDataPlane;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.Session;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnRegisterEndpointTest extends RxWebTestBase {

    @Mock
    private TemplateEngine templateEngine;
    @Mock
    private Domain domain;
    @Mock
    private DomainDataPlane domainDataPlane;
    @Mock
    private FactorManager factorManager;

    private WebAuthnRegisterEndpoint endpoint;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        when(domainDataPlane.getDomain()).thenReturn(domain);
        endpoint = new WebAuthnRegisterEndpoint(templateEngine, domainDataPlane, factorManager);

        router.route()
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(BodyHandler.create());
    }

    @Test
    public void shouldSetFlowMarker_whenPageRendered() throws Exception {
        when(templateEngine.render(anyMap(), any())).thenReturn(Single.just(Buffer.buffer()));

        final AtomicReference<Session> sessionRef = new AtomicReference<>();

        router.route(HttpMethod.GET, "/webauthn/register")
                .handler(rc -> {
                    User endUser = new User();
                    endUser.setUsername("username");
                    rc.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, new Client());
                    sessionRef.set(rc.session());
                    rc.next();
                })
                .handler(endpoint);

        testRequest(HttpMethod.GET,
                "/webauthn/register",
                null,
                null,
                HttpStatusCode.OK_200, "OK", null);

        Assert.assertEquals(Boolean.TRUE, sessionRef.get().get(ConstantKeys.WEBAUTHN_REGISTER_FLOW_ONGOING_KEY));
    }
}
