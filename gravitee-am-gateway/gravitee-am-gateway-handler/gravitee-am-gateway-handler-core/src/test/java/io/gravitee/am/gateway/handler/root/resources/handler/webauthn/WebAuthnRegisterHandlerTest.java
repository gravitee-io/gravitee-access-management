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
package io.gravitee.am.gateway.handler.root.resources.handler.webauthn;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DomainDataPlane;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.Credentials;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import io.vertx.rxjava3.ext.auth.webauthn.WebAuthn;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.vertx.core.http.HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnRegisterHandlerTest extends RxWebTestBase {

    private static final String PASSWORDLESS_CHALLENGE = "KidEI7a_CXoW7dKjb8JEnL_WUb2xDPWBHtCUzTo4ouAyKNzc0SbGmC0t8mh0cKHdqo_4hYc3c2Iewp_QF746rA";
    private static final String PASSWORDLESS_USERNAME = "username";

    @Mock
    private Domain domain;
    @Mock
    private DomainDataPlane domainDataPlane;
    @Mock
    private UserService userService;
    @Mock
    private FactorManager factorManager;
    @Mock
    private CredentialGatewayService credentialService;
    @Mock
    private WebAuthn webAuthn;

    private WebAuthnRegisterHandler webAuthnRegisterHandler;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        when(domainDataPlane.getDomain()).thenReturn(domain);
        when(domainDataPlane.getWebAuthnOrigin()).thenReturn("http://localhost:8092");

        webAuthnRegisterHandler = new WebAuthnRegisterHandler(userService, factorManager, domainDataPlane, webAuthn, credentialService);

        router.route()
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(BodyHandler.create());
    }

    @Test
    public void shouldFail403_v1_whenFlowMarkerAbsent() throws Exception {
        router.route(HttpMethod.POST, "/webauthn/register")
                .handler(authenticatedUser(false))
                .handler(webAuthnRegisterHandler)
                .handler(rc -> rc.end());

        testRequest(HttpMethod.POST,
                "/webauthn/register",
                sendAssertion(),
                403,
                "Forbidden", null);
    }

    @Test
    public void shouldFail403_v0_whenFlowMarkerAbsent() throws Exception {
        router.route(HttpMethod.POST, "/webauthn/register")
                .handler(authenticatedUser(false))
                .handler(webAuthnRegisterHandler)
                .handler(rc -> rc.end());

        testRequest(HttpMethod.POST,
                "/webauthn/register",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, "application/json");
                    req.write(Buffer.buffer(new JsonObject()
                            .put("name", "johndoe")
                            .put("displayName", "John Doe")
                            .encode()));
                },
                403,
                "Forbidden", null);
    }

    @Test
    public void shouldRegister_v1_whenFlowMarkerPresent_andConsumeMarker() throws Exception {
        when(webAuthn.authenticate(any(Credentials.class)))
                .thenReturn(Single.just(io.vertx.rxjava3.ext.auth.User.fromName(PASSWORDLESS_USERNAME)));
        when(credentialService.findByCredentialId(any(), any())).thenReturn(Flowable.just(new Credential()));
        when(credentialService.update(any(), any(), any())).thenReturn(Single.just(new Credential()));

        final AtomicReference<Object> markerAfterRegistration = new AtomicReference<>("not-set");

        router.route(HttpMethod.POST, "/webauthn/register")
                .handler(authenticatedUser(true))
                .handler(webAuthnRegisterHandler)
                .handler(rc -> {
                    markerAfterRegistration.set(rc.session().get(ConstantKeys.WEBAUTHN_REGISTER_FLOW_ONGOING_KEY));
                    rc.end();
                });

        testRequest(HttpMethod.POST,
                "/webauthn/register",
                sendAssertion(),
                200,
                "OK", null);

        Assert.assertNull("flow-integrity marker must be removed after a successful registration", markerAfterRegistration.get());
    }

    private Consumer<HttpClientRequest> sendAssertion() {
        return req -> {
            req.headers().set(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
            req.setChunked(true);
            req.write(Buffer.buffer("assertion=%7B%22id%22%3A%22QAn8i7wToriAx38jzUrVw04Cxao_y285vX2CGwyFtO8%22%2C%22rawId%22%3A%22QAn8i7wToriAx38jzUrVw04Cxao_y285vX2CGwyFtO8%22%2C%22response%22%3A%7B%22clientDataJSON%22%3A%22eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdlIjoiS2lkRUk3YV9DWG9XN2RLamI4SkVuTF9XVWIyeERQV0JIdENVelRvNG91QXlLTnpjMFNiR21DMHQ4bWgwY0tIZHFvXzRoWWMzYzJJZXdwX1FGNzQ2ckEiLCJvcmlnaW4iOiJodHRwOi8vbG9jYWxob3N0OjgwOTIiLCJjcm9zc09yaWdpbiI6ZmFsc2V9%22%2C%22authenticatorData%22%3A%22SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MBAAAAAg%22%2C%22signature%22%3A%22MEUCIBfUoc-FHIliWp_6bw2c74Bnx6d62eDce0klo2O79ksjAiEA5c0JmD5o9CGacnx3bjOausUFRobNhYlJatPqRZwObn0%22%2C%22userHandle%22%3A%22%22%7D%2C%22type%22%3A%22public-key%22%7D"));
        };
    }

    private io.vertx.core.Handler<io.vertx.rxjava3.ext.web.RoutingContext> authenticatedUser(boolean withFlowMarker) {
        return rc -> {
            User endUser = new User();
            endUser.setId("user-id");
            rc.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, new Client());
            rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, PASSWORDLESS_CHALLENGE);
            rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, PASSWORDLESS_USERNAME);
            if (withFlowMarker) {
                rc.session().put(ConstantKeys.WEBAUTHN_REGISTER_FLOW_ONGOING_KEY, true);
            }
            rc.next();
        };
    }
}
