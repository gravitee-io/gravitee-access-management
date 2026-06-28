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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class GatewayCallbackClientTest {

    static WireMockServer wm; static Vertx vertx;
    @BeforeAll static void up() { wm = new WireMockServer(0); wm.start(); vertx = Vertx.vertx(); }
    @AfterAll static void down() { wm.stop(); vertx.close(); }
    @BeforeEach void reset() { wm.resetAll(); }

    @Test
    void posts_client_authed_form_and_completes_on_200() {
        wm.stubFor(post(urlEqualTo("/cb")).willReturn(aResponse().withStatus(200)));
        var c = new GatewayCallbackClient(WebClient.create(vertx), "cbid", "cbsecret", "client_secret_post");
        c.postCallback("http://localhost:" + wm.port() + "/cb", "stateJwt", "tid1", true, null, null).blockingAwait();
        wm.verify(postRequestedFor(urlEqualTo("/cb"))
                .withRequestBody(containing("state=stateJwt"))
                .withRequestBody(containing("tid=tid1"))
                .withRequestBody(containing("validated=true"))
                .withRequestBody(containing("client_id=cbid"))
                .withRequestBody(containing("client_secret=cbsecret")));
    }

    @Test
    void posts_id_token_when_present() {
        wm.stubFor(post(urlEqualTo("/cb")).willReturn(aResponse().withStatus(200)));
        var c = new GatewayCallbackClient(WebClient.create(vertx), "cbid", "cbsecret", "client_secret_post");
        c.postCallback("http://localhost:" + wm.port() + "/cb", "s", "tid1", true, "eyJ.id.tok", null).blockingAwait();
        wm.verify(postRequestedFor(urlEqualTo("/cb")).withRequestBody(containing("id_token=eyJ.id.tok")));
    }

    @Test
    void errors_when_callback_rejects() {
        wm.stubFor(post(urlEqualTo("/cb")).willReturn(aResponse().withStatus(400)));
        var c = new GatewayCallbackClient(WebClient.create(vertx), "cbid", "cbsecret", "client_secret_post");
        assertThrows(Exception.class, () ->
                c.postCallback("http://localhost:" + wm.port() + "/cb", "s", "t", false, null, null).blockingAwait());
    }

    @Test
    void basic_auth_sends_header_and_no_secret_in_body() {
        wm.stubFor(post(urlEqualTo("/cb")).willReturn(aResponse().withStatus(200)));
        var c = new GatewayCallbackClient(WebClient.create(vertx), "cbid", "cbsecret", "client_secret_basic");
        c.postCallback("http://localhost:" + wm.port() + "/cb", "s", "tid1", true, null, null).blockingAwait();
        wm.verify(postRequestedFor(urlEqualTo("/cb"))
                .withHeader("Authorization", matching("Basic .+"))
                .withRequestBody(containing("client_id=cbid"))
                .withRequestBody(notMatching(".*client_secret=.*")));
    }
}
