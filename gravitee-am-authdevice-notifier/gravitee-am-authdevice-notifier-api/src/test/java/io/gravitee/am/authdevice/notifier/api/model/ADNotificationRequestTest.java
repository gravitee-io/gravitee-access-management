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

class ADNotificationRequestTest {
    @Test
    void carries_federated_connection_bundle() {
        ADNotificationRequest req = new ADNotificationRequest();
        req.setConnection(new FederatedConnection("cid", "secret", "openid profile email", "https://dev.eu.auth0.com/.well-known/openid-configuration"));
        assertEquals("cid", req.getConnection().clientId());
        assertEquals("openid profile email", req.getConnection().scope());
    }

    @Test void callback_url_round_trips() {
        var req = new ADNotificationRequest();
        req.setCallbackUrl("https://gw/dom/oidc/ciba/authenticate/callback");
        assertEquals(
            "https://gw/dom/oidc/ciba/authenticate/callback", req.getCallbackUrl());
    }
}
