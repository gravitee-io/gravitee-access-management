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
package io.gravitee.am.authdevice.notifier.cibafederation.provider.spring;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SchemaFormJsonTest {
    @Test
    void schema_is_valid_json_with_expected_fields() throws Exception {
        String raw = Files.readString(Path.of("src/main/resources/schemas/schema-form.json"));
        JsonObject schema = new JsonObject(raw);
        JsonObject props = schema.getJsonObject("properties");
        for (String f : new String[]{"identityProviderId","resourceAudience","callbackClientId","callbackClientSecret",
                "recipientDisplayName","maxLifetimeSeconds","consentRelayStrategy"}) {
            assertNotNull(props.getJsonObject(f), "missing schema property: " + f);
        }
        // callbackBaseUrl and pollIntervalCapSeconds were dropped from the schema; callbackUrl is supplied per-request.
        for (String removed : new String[]{"callbackBaseUrl","pollIntervalCapSeconds"}) {
            assertNull(props.getJsonObject(removed), "schema must not contain removed property: " + removed);
        }
        assertTrue(schema.getJsonArray("required").contains("identityProviderId"));
        assertTrue(schema.getJsonArray("required").contains("callbackClientId"));
        assertTrue(schema.getJsonArray("required").contains("callbackClientSecret"));

        JsonObject strat = props.getJsonObject("consentRelayStrategy");
        assertEquals("string", strat.getString("type"));
        assertFalse(strat.containsKey("enum"), "consentRelayStrategy must not be an enum (open, module-contributed ids)");
        assertFalse(strat.containsKey("default"), "blank = raw relay; no default");
    }

    @Test
    void schema_declares_callback_client_auth_method_enum() throws Exception {
        String raw = Files.readString(Path.of("src/main/resources/schemas/schema-form.json"));
        JsonObject schema = new JsonObject(raw);
        JsonObject props = schema.getJsonObject("properties");

        JsonObject authMethod = props.getJsonObject("callbackClientAuthMethod");
        assertNotNull(authMethod, "schema must declare callbackClientAuthMethod");
        assertEquals("client_secret_post", authMethod.getString("default"));
        assertTrue(authMethod.getJsonArray("enum").contains("client_secret_basic"),
                "schema must offer client_secret_basic");
    }
}
