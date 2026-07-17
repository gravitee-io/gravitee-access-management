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

import java.util.concurrent.atomic.AtomicLong;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

class OidcDiscoveryResolverTest {
    WireMockServer wm;
    WebClient webClient;
    String wellKnown;

    @BeforeEach void setUp() {
        wm = new WireMockServer(options().dynamicPort()); wm.start();
        webClient = WebClient.create(Vertx.vertx());
        wellKnown = "http://localhost:" + wm.port() + "/.well-known/openid-configuration";
    }
    @AfterEach void tearDown() { wm.stop(); }

    private void stubDiscovery(String bc, String token) {
        wm.stubFor(get(urlEqualTo("/.well-known/openid-configuration")).willReturn(okJson(
            "{\"issuer\":\"https://op/\",\"backchannel_authentication_endpoint\":\"" + bc + "\",\"token_endpoint\":\"" + token + "\"}")));
    }

    @Test void resolves_and_caches_endpoints() {
        stubDiscovery("https://op/bc", "https://op/token");
        var r = new OidcDiscoveryResolver(webClient, 3600);
        var ep = r.resolve(wellKnown).blockingGet();
        assertEquals("https://op/", ep.issuer());
        assertEquals("https://op/bc", ep.backchannelAuthEndpoint());
        assertEquals("https://op/token", ep.tokenEndpoint());
        r.resolve(wellKnown).blockingGet(); // second call served from cache
        wm.verify(1, getRequestedFor(urlEqualTo("/.well-known/openid-configuration")));
    }

    @Test void fails_closed_when_issuer_missing() {
        wm.stubFor(get(urlEqualTo("/.well-known/openid-configuration"))
            .willReturn(okJson("{\"backchannel_authentication_endpoint\":\"https://op/bc\",\"token_endpoint\":\"https://op/token\"}")));
        var r = new OidcDiscoveryResolver(webClient, 3600);
        assertThrows(IllegalStateException.class, () -> r.resolve(wellKnown).blockingGet());
    }

    @Test void refetches_after_ttl_expiry() {
        stubDiscovery("https://op/bc", "https://op/token");
        AtomicLong now = new AtomicLong(0L);
        var r = new OidcDiscoveryResolver(webClient, 10, now::get); // 10s TTL
        r.resolve(wellKnown).blockingGet();
        now.set(11_000L); // advance past TTL
        r.resolve(wellKnown).blockingGet();
        wm.verify(2, getRequestedFor(urlEqualTo("/.well-known/openid-configuration")));
    }

    @Test void fails_closed_on_non_200() {
        wm.stubFor(get(urlEqualTo("/.well-known/openid-configuration")).willReturn(aResponse().withStatus(404)));
        var r = new OidcDiscoveryResolver(webClient, 3600);
        assertThrows(IllegalStateException.class, () -> r.resolve(wellKnown).blockingGet());
    }

    @Test void fails_closed_when_backchannel_endpoint_missing() {
        wm.stubFor(get(urlEqualTo("/.well-known/openid-configuration"))
            .willReturn(okJson("{\"token_endpoint\":\"https://op/token\"}")));
        var r = new OidcDiscoveryResolver(webClient, 3600);
        assertThrows(IllegalStateException.class, () -> r.resolve(wellKnown).blockingGet());
    }

    @Test void fails_closed_on_blank_wellknown() {
        var r = new OidcDiscoveryResolver(webClient, 3600);
        assertThrows(IllegalStateException.class, () -> r.resolve("  ").blockingGet());
    }

    @Test void fails_closed_when_token_endpoint_missing() {
        wm.stubFor(get(urlEqualTo("/.well-known/openid-configuration"))
            .willReturn(okJson("{\"backchannel_authentication_endpoint\":\"https://op/bc\"}")));
        var r = new OidcDiscoveryResolver(webClient, 3600);
        assertThrows(IllegalStateException.class, () -> r.resolve(wellKnown).blockingGet());
    }

    @Test void fails_closed_on_non_json_body() {
        wm.stubFor(get(urlEqualTo("/.well-known/openid-configuration"))
            .willReturn(aResponse().withStatus(200).withBody("not json").withHeader("Content-Type", "text/plain")));
        var r = new OidcDiscoveryResolver(webClient, 3600);
        assertThrows(Exception.class, () -> r.resolve(wellKnown).blockingGet());
    }
}
