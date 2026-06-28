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

class ADUserResponseTest {
    @Test
    void carries_access_and_id_token() {
        ADUserResponse r = new ADUserResponse("tid", "state", true, "id-tok", "acc-tok");
        assertEquals("id-tok", r.getIdToken());
        assertEquals("acc-tok", r.getAccessToken());
    }
    @Test
    void backward_compatible_constructors_default_tokens_null() {
        assertNull(new ADUserResponse("tid", "state", true).getAccessToken());
        assertNull(new ADUserResponse("tid", "state", true, "id-tok").getAccessToken());
    }

    @Test
    void carries_identity_provider_id() {
        ADUserResponse r = new ADUserResponse("tid", "state", true, "id-tok", "acc-tok", "idp-1");
        assertEquals("idp-1", r.getIdentityProviderId());
    }

    @Test
    void identity_provider_id_defaults_null_on_shorter_constructors() {
        assertNull(new ADUserResponse("tid", "state", true).getIdentityProviderId());
        assertNull(new ADUserResponse("tid", "state", true, "id-tok").getIdentityProviderId());
        assertNull(new ADUserResponse("tid", "state", true, "id-tok", "acc-tok").getIdentityProviderId());
    }
}
