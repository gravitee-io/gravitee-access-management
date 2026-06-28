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
package io.gravitee.am.authdevice.notifier.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FederatedConnectionTest {

    @Test
    void five_arg_ctor_sets_client_auth_method() {
        FederatedConnection c = new FederatedConnection("cid", "sec", "openid", "https://wk", "client_secret_basic");
        assertEquals("client_secret_basic", c.clientAuthMethod());
    }

    @Test
    void four_arg_convenience_ctor_defaults_to_post() {
        FederatedConnection c = new FederatedConnection("cid", "sec", "openid", "https://wk");
        assertEquals("client_secret_post", c.clientAuthMethod());
    }

    @Test
    void tostring_includes_method_and_redacts_secret() {
        String s = new FederatedConnection("cid", "sup3r-secret", "openid", "https://wk", "client_secret_basic").toString();
        assertTrue(s.contains("clientAuthMethod=client_secret_basic"), s);
        assertTrue(s.contains("clientSecret=REDACTED"), s);
        assertFalse(s.contains("sup3r-secret"), "secret must never appear in toString");
    }
}
