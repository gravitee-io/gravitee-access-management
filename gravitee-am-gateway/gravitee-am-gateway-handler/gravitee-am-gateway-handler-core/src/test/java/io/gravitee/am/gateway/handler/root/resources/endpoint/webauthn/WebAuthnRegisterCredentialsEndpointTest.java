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
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.service.DomainDataPlane;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.WebAuthn;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static io.gravitee.am.gateway.handler.common.vertx.web.RoutingContextHelper.setUser;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnRegisterCredentialsEndpointTest extends RxWebTestBase {

    @Mock
    private Domain domain;
    @Mock
    private DomainDataPlane domainDataPlane;
    @Mock
    private WebAuthn webAuthn;

    private WebAuthnRegisterCredentialsEndpoint endpoint;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        when(domainDataPlane.getDomain()).thenReturn(domain);
        endpoint = new WebAuthnRegisterCredentialsEndpoint(domainDataPlane, webAuthn);

        router.route()
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(BodyHandler.create());
    }

    @Test
    public void shouldFail403_whenFlowMarkerAbsent() throws Exception {
        router.route(HttpMethod.POST, "/webauthn/register/credentials")
                .handler(rc -> {
                    User endUser = new User();
                    endUser.setId("user-id");
                    setUser(rc, endUser);
                    rc.next();
                })
                .handler(endpoint);

        testRequest(HttpMethod.POST,
                "/webauthn/register/credentials",
                this::sendCredentialsRequest,
                403,
                "Forbidden", null);

        verify(webAuthn, never()).createCredentialsOptions(any());
    }

    @Test
    public void shouldReturnOptions_whenFlowMarkerPresent() throws Exception {
        JsonObject options = new JsonObject()
                .put("challenge", "challenge-value")
                .put("user", new JsonObject());
        when(webAuthn.createCredentialsOptions(any())).thenReturn(Future.succeededFuture(options));
        router.route(HttpMethod.POST, "/webauthn/register/credentials")
                .handler(rc -> {
                    User endUser = new User();
                    endUser.setId("user-id");
                    setUser(rc, endUser);
                    rc.session().put(ConstantKeys.WEBAUTHN_REGISTER_FLOW_ONGOING_KEY, true);
                    rc.next();
                })
                .handler(endpoint);

        testRequest(HttpMethod.POST,
                "/webauthn/register/credentials",
                this::sendCredentialsRequest,
                200,
                "OK", null);

        verify(webAuthn).createCredentialsOptions(any());
    }

    private void sendCredentialsRequest(io.vertx.rxjava3.core.http.HttpClientRequest req) {
        req.setChunked(true);
        req.putHeader(CONTENT_TYPE, "application/json");
        req.write(Buffer.buffer(new JsonObject()
                .put("name", "johndoe")
                .put("displayName", "John Doe")
                .encode()));
    }
}
