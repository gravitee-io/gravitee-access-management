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
package io.gravitee.am.gateway.handler.aauth.service;

import io.gravitee.am.gateway.handler.aauth.test.fixtures.MockAgentMetadataServer;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AgentMetadataFetcherTest {

    private MockAgentMetadataServer mockServer;
    private AgentMetadataFetcher fetcher;

    @Before
    public void setUp() {
        mockServer = new MockAgentMetadataServer();
        // Start first to get the dynamic port, then configure stubs
        mockServer.start();
        mockServer.stubMetadata(mockServer.baseUrl(), mockServer.baseUrl() + "/jwks.json", "Test Agent");
        mockServer.stubJwksWithEd25519("key-1", TestAgentKeyPairFactory.ed25519PublicKeyX());

        fetcher = new AgentMetadataFetcher();
    }

    @After
    public void tearDown() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Test
    public void shouldFetchMetadata() throws Exception {
        AgentMetadata metadata = fetcher.fetchMetadata(mockServer.baseUrl());

        assertNotNull(metadata);
        assertEquals(mockServer.baseUrl(), metadata.issuer());
        assertEquals("Test Agent", metadata.clientName());
    }

    @Test
    public void shouldFetchJWKS() throws Exception {
        AgentMetadata metadata = fetcher.fetchMetadata(mockServer.baseUrl());
        JWKSDocument jwks = fetcher.fetchJWKS(metadata.jwksUri());

        assertNotNull(jwks);
        assertNotNull(jwks.findByKid("key-1"));
    }

    @Test
    public void shouldCacheMetadata() throws Exception {
        fetcher.fetchMetadata(mockServer.baseUrl());
        fetcher.fetchMetadata(mockServer.baseUrl());

        assertEquals("Should have fetched metadata only once", 1, mockServer.metadataRequestCount());
    }

    @Test
    public void shouldCacheJWKS() throws Exception {
        AgentMetadata metadata = fetcher.fetchMetadata(mockServer.baseUrl());
        fetcher.fetchJWKS(metadata.jwksUri());
        fetcher.fetchJWKS(metadata.jwksUri());

        assertEquals("Should have fetched JWKS only once", 1, mockServer.jwksRequestCount());
    }

    @Test
    public void shouldRefreshJWKSOnForceRefresh() throws Exception {
        AgentMetadata metadata = fetcher.fetchMetadata(mockServer.baseUrl());
        fetcher.fetchJWKS(metadata.jwksUri());
        fetcher.fetchJWKS(metadata.jwksUri(), true);

        assertEquals("Should have fetched JWKS twice (initial + forced)", 2, mockServer.jwksRequestCount());
    }
}
