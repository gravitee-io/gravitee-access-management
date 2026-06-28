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

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class ClientAuthenticationTest {

    // Real Vert.x, created/closed cleanly (mirrors CibaClientTest / GatewayCallbackClientTest) so
    // the event loop is released and the run leaves no leaked threads behind.
    static Vertx vertx;
    static WebClient webClient;

    @BeforeAll static void up() { vertx = Vertx.vertx(); webClient = WebClient.create(vertx); }
    @AfterAll static void down() { vertx.close(); }

    @Test
    void supported_set_is_post_and_basic_only() {
        assertTrue(ClientAuthentication.isSupported("client_secret_post"));
        assertTrue(ClientAuthentication.isSupported("client_secret_basic"));
        assertTrue(ClientAuthentication.isSupported(null), "null defaults to post");
        assertTrue(ClientAuthentication.isSupported("  "), "blank defaults to post");
        assertFalse(ClientAuthentication.isSupported("private_key_jwt"));
        assertFalse(ClientAuthentication.isSupported("client_secret_jwt"));
        assertFalse(ClientAuthentication.isSupported("tls_client_auth"));
        assertFalse(ClientAuthentication.isSupported("none"));
    }

    @Test
    void unsupported_message_names_the_method() {
        assertEquals("CIBA federation supports client_secret_post and client_secret_basic; 'private_key_jwt' is not supported",
                ClientAuthentication.unsupportedMessage("private_key_jwt"));
    }

    @Test
    void basic_header_url_encodes_per_rfc6749_and_round_trips_through_am_decoder() {
        String header = ClientAuthentication.basicHeader("cli+ent", "se cr:et");
        assertTrue(header.startsWith("Basic "));
        String decoded = new String(Base64.getDecoder().decode(header.substring("Basic ".length())), UTF_8);
        // '+' -> %2B, space -> %20, ':' inside the secret -> %3A; the separator ':' stays literal
        assertEquals("cli%2Bent:se%20cr%3Aet", decoded);
        String[] parts = decoded.split(":", 2);
        assertEquals("cli+ent", amDecode(parts[0]));
        assertEquals("se cr:et", amDecode(parts[1]));
    }

    // applied() implements the task's top invariant: client_secret is sent by exactly one
    // mechanism. A real MultiMap (the production form collaborator) and a real HttpRequest let us
    // inspect req.headers() directly after the call, with no mocking.

    @Test
    void applied_basic_sets_auth_header_and_never_the_form_secret() {
        // Caller has already set client_id; applied() must not touch it.
        MultiMap form = MultiMap.caseInsensitiveMultiMap().set("client_id", "cli+ent");
        HttpRequest<Buffer> req = webClient.postAbs("http://localhost/x");

        HttpRequest<Buffer> returned = ClientAuthentication.applied(req, form, "client_secret_basic", "cli+ent", "se cr:et");

        assertSame(req, returned, "applied() returns the same request it was given");
        // client_secret travels in the Authorization header only.
        assertTrue(req.headers().get("Authorization").startsWith("Basic "), "basic sets a Basic Authorization header");
        assertNull(form.get("client_secret"), "basic must NOT put the secret in the form");
        assertEquals("cli+ent", form.get("client_id"), "applied() must leave client_id untouched");
    }

    @Test
    void applied_post_sets_form_secret_and_never_the_auth_header() {
        MultiMap form = MultiMap.caseInsensitiveMultiMap().set("client_id", "cli+ent");
        HttpRequest<Buffer> req = webClient.postAbs("http://localhost/x");

        HttpRequest<Buffer> returned = ClientAuthentication.applied(req, form, "client_secret_post", "cli+ent", "se cr:et");

        assertSame(req, returned, "applied() returns the same request it was given");
        // client_secret travels in the form body only.
        assertEquals("se cr:et", form.get("client_secret"));
        assertNull(req.headers().get("Authorization"), "post must NOT set an Authorization header");
        assertEquals("cli+ent", form.get("client_id"), "applied() must leave client_id untouched");
    }

    /** Mirrors AM's ClientBasicAuthProvider.urlDecode (preserves a literal '+'). */
    private static String amDecode(String v) {
        return URLDecoder.decode(v.replace("+", "%2B"), UTF_8);
    }
}
