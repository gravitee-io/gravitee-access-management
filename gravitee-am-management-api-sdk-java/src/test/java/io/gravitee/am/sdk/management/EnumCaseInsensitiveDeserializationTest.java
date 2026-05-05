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
package io.gravitee.am.sdk.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.sdk.management.model.Application;
import io.gravitee.am.sdk.management.model.PatchApplication;
import io.gravitee.am.sdk.management.model.PatchApplicationSettings;
import io.gravitee.am.sdk.management.model.PatchApplicationType;
import io.gravitee.am.sdk.management.model.PatchDomain;
import io.gravitee.am.sdk.management.model.PatchOIDCSettings;
import io.gravitee.am.sdk.management.model.PatchSAMLSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The Management API serializes enums as lowercase ("web", "agent", ...) while the
 * generated SDK declares uppercase enum constants. The openapi-generator
 * useEnumCaseInsensitive option makes fromValue() use equalsIgnoreCase so wire
 * payloads decode cleanly. These tests guard that the option is wired up across
 * generated enums.
 */
class EnumCaseInsensitiveDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesLowercaseApplicationType() throws Exception {
        Application app = objectMapper.readValue("{\"type\":\"web\"}", Application.class);
        assertEquals(Application.TypeEnum.WEB, app.getType());
    }

    @Test
    void deserializesLowercasePatchApplicationType() throws Exception {
        PatchApplicationType patch = objectMapper.readValue("{\"type\":\"service\"}", PatchApplicationType.class);
        assertEquals(PatchApplicationType.TypeEnum.SERVICE, patch.getType());
    }

    @Test
    void enumFromValueAcceptsLowercase() {
        assertEquals(Application.TypeEnum.NATIVE, Application.TypeEnum.fromValue("native"));
        assertEquals(PatchApplicationType.TypeEnum.BROWSER, PatchApplicationType.TypeEnum.fromValue("browser"));
    }

    /**
     * `requiredPermissions` is a derived getter on the AM service-side Patch* models used
     * internally for permission enforcement (see PermissionSettingUtils). The SDK must not
     * round-trip it on the wire — AM rejects the unknown property with HTTP 400. The pom's
     * pre-generation transform strips it from the spec for these schemas.
     */
    @Test
    void patchModelsDoNotSerializeRequiredPermissions() throws Exception {
        assertFalse(objectMapper.writeValueAsString(new PatchApplication()).contains("requiredPermissions"));
        assertFalse(objectMapper.writeValueAsString(new PatchApplicationSettings()).contains("requiredPermissions"));
        assertFalse(objectMapper.writeValueAsString(new PatchDomain()).contains("requiredPermissions"));
        assertFalse(objectMapper.writeValueAsString(new PatchOIDCSettings()).contains("requiredPermissions"));
        assertFalse(objectMapper.writeValueAsString(new PatchSAMLSettings()).contains("requiredPermissions"));
    }
}
