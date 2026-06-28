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
        // callbackBaseUrl and pollIntervalCapSeconds removed in Tasks 11/12; callbackUrl is per-request.
        for (String removed : new String[]{"callbackBaseUrl","pollIntervalCapSeconds",
                "auth0Domain","auth0ClientId","auth0ClientSecret","auth0Audience"}) {
            assertNull(props.getJsonObject(removed), "schema must not contain removed property: " + removed);
        }
        assertTrue(schema.getJsonArray("required").contains("identityProviderId"));
        assertTrue(schema.getJsonArray("required").contains("callbackClientId"));
        assertTrue(schema.getJsonArray("required").contains("callbackClientSecret"));
    }
}
