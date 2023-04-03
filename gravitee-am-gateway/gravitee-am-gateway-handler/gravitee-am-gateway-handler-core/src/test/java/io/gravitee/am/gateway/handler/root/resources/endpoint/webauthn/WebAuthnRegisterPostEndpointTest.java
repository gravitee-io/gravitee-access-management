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
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static io.vertx.core.http.HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnRegisterPostEndpointTest extends RxWebTestBase {

    @Mock
    private Domain domain;
    private WebAuthnRegisterPostEndpoint webAuthnRegisterPostEndpoint;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        webAuthnRegisterPostEndpoint = new WebAuthnRegisterPostEndpoint(domain);

        router.route()
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(BodyHandler.create());
    }

    @Test
    public void shouldNotRedirectToDeviceNamingPage_optionDisabled() throws Exception {
        router.route(HttpMethod.POST, "/webauthn/register")
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, new Client());
                    rc.next();
                })
                .handler(webAuthnRegisterPostEndpoint);

        testRequest(HttpMethod.POST,
                "/webauthn/register",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
                    req.write(Buffer.buffer("credential=credential"));
                },
                res -> {
                    String location = res.getHeader("Location");
                    Assert.assertTrue(location.contains("/oauth/authorize"));
                },
                302,
                "Found", null);
    }

    @Test
    public void shouldRedirectToDeviceNamingPage_optionEnabled() throws Exception {
        router.route(HttpMethod.POST, "/webauthn/register")
                .handler(rc -> {
                    Client client = new Client();
                    LoginSettings loginSettings = new LoginSettings();
                    loginSettings.setInherited(false);
                    loginSettings.setPasswordlessEnabled(true);
                    loginSettings.setPasswordlessDeviceNamingEnabled(true);
                    client.setLoginSettings(loginSettings);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                })
                .handler(webAuthnRegisterPostEndpoint);

        testRequest(HttpMethod.POST,
                "/webauthn/register",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
                    req.write(Buffer.buffer("credential=credential"));
                },
                res -> {
                    String location = res.getHeader("Location");
                    Assert.assertTrue(location.contains("/webauthn/register/success"));
                },
                302,
                "Found", null);
    }
}
