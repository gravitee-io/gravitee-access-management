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
package io.gravitee.am.gateway.handler.aauth.resources.endpoint;

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Tests for the AAUTH PS metadata endpoint.
 *
 * @author GraviteeSource Team
 */
public class AAuthPSMetadataEndpointTest extends RxWebTestBase {

    private static final String METADATA_PATH = "/aauth/.well-known/aauth-person.json";

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(METADATA_PATH)
                .handler(rc -> {
                    rc.put(CONTEXT_PATH, "/testdomain");
                    rc.next();
                })
                .handler(new AAuthPSMetadataEndpoint());
    }

    @Test
    public void shouldReturn200_withApplicationJsonContentType() throws Exception {
        testRequest(
                HttpMethod.GET,
                METADATA_PATH,
                null,
                resp -> {
                    String contentType = resp.getHeader("Content-Type");
                    assertNotNull(contentType);
                    assertTrue(contentType.contains("application/json"));

                    String cacheControl = resp.getHeader("Cache-Control");
                    assertNotNull(cacheControl);
                    assertTrue(cacheControl.contains("public"));
                    assertTrue(cacheControl.contains("max-age=3600"));
                },
                200,
                "OK",
                null
        );
    }

    @Test
    public void shouldReturnThreeRequiredFields() throws Exception {
        JsonObject json = fetchMetadataJson();

        assertEquals("Should have exactly 3 fields", 3, json.size());
        assertNotNull("issuer should be present", json.getString("issuer"));
        assertNotNull("token_endpoint should be present", json.getString("token_endpoint"));
        assertNotNull("jwks_uri should be present", json.getString("jwks_uri"));
    }

    @Test
    public void shouldReturnAbsoluteUrls() throws Exception {
        JsonObject json = fetchMetadataJson();

        String issuer = json.getString("issuer");
        String tokenEndpoint = json.getString("token_endpoint");
        String jwksUri = json.getString("jwks_uri");

        assertTrue("issuer should contain context path", issuer.contains("/testdomain"));
        assertTrue("token_endpoint should contain context path", tokenEndpoint.contains("/testdomain"));
        assertTrue("jwks_uri should contain context path", jwksUri.contains("/testdomain"));

        assertTrue("token_endpoint should end with /aauth/token", tokenEndpoint.endsWith("/aauth/token"));
        assertTrue("jwks_uri should end with /aauth/.well-known/jwks.json",
                jwksUri.endsWith("/aauth/.well-known/jwks.json"));
    }

    @Test
    public void shouldUseLowercaseIssuerWithoutTrailingSlash() throws Exception {
        JsonObject json = fetchMetadataJson();
        String issuer = json.getString("issuer");

        assertFalse("issuer should not end with /", issuer.endsWith("/"));

        String pathPart = issuer.substring(issuer.indexOf("/testdomain"));
        assertEquals("issuer path should be lowercase", pathPart.toLowerCase(), pathPart);
    }

    /**
     * Helper: makes a raw HTTP GET to the metadata endpoint and returns the parsed JSON.
     */
    private JsonObject fetchMetadataJson() throws Exception {
        URL url = new URL("http://localhost:" + lookupAvailablePort() + METADATA_PATH);
        // Use the server port from the test base — access via a direct HTTP call
        HttpURLConnection conn = (HttpURLConnection) new URL(
                "http://localhost:" + server.actualPort() + METADATA_PATH
        ).openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String body = reader.lines().collect(Collectors.joining("\n"));
            return new JsonObject(body);
        }
    }
}
