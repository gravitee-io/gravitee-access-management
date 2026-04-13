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
package io.gravitee.am.gateway.handler.aauth.test.fixtures;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * WireMock-backed mock agent server that serves metadata and JWKS documents.
 * <p>
 * Usage: start first (to get the dynamic port), then configure stubs with the actual URLs.
 */
public class MockAgentMetadataServer {

    private final WireMockServer server;

    public MockAgentMetadataServer() {
        this.server = new WireMockServer(wireMockConfig().dynamicPort());
    }

    public MockAgentMetadataServer start() {
        server.start();
        WireMock.configureFor("localhost", server.port());
        return this;
    }

    public void stop() {
        server.stop();
    }

    public String baseUrl() {
        return "http://localhost:" + server.port();
    }

    /**
     * Stub the agent metadata endpoint after server is started.
     */
    public void stubMetadata(String issuer, String jwksUri, String clientName) {
        stubFor(get(urlEqualTo("/.well-known/aauth-agent.json"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"issuer":"%s","jwks_uri":"%s","client_name":"%s"}
                                """.formatted(issuer, jwksUri, clientName))));
    }

    /**
     * Stub the JWKS endpoint with a single Ed25519 key.
     */
    public void stubJwksWithEd25519(String kid, String x) {
        stubFor(get(urlEqualTo("/jwks.json"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/jwk-set+json")
                        .withBody("""
                                {"keys":[{"kty":"OKP","crv":"Ed25519","kid":"%s","x":"%s"}]}
                                """.formatted(kid, x))));
    }

    public int jwksRequestCount() {
        return server.countRequestsMatching(getRequestedFor(urlEqualTo("/jwks.json")).build()).getCount();
    }

    public int metadataRequestCount() {
        return server.countRequestsMatching(getRequestedFor(urlEqualTo("/.well-known/aauth-agent.json")).build()).getCount();
    }
}
