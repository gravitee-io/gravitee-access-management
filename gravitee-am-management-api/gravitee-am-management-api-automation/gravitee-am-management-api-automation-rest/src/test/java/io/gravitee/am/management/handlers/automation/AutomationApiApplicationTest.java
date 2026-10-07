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
package io.gravitee.am.management.handlers.automation;

import io.gravitee.am.management.handlers.management.api.model.ErrorEntity;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request the Automation API cannot read is a client error: it is answered with a 4xx JSON
 * {@link ErrorEntity}, as on the management API, never with a 500 or a parser message.
 *
 * @author GraviteeSource Team
 */
class AutomationApiApplicationTest extends AutomationJerseySpringTest {

    @Test
    void malformed_json_is_rejected_without_parser_details() {
        Response response = domainsTarget().request()
                .put(Entity.entity("{\"key\":", MediaType.APPLICATION_JSON_TYPE));

        assertEquals(400, response.getStatus());
        assertTrue(MediaType.APPLICATION_JSON_TYPE.isCompatible(response.getMediaType()));
        ErrorEntity error = readEntity(response, ErrorEntity.class);
        assertEquals("Malformed json", error.getMessage());
        assertEquals(400, error.getHttpCode());
    }

    @Test
    void unknown_property_is_rejected_naming_it() {
        Response response = domainsTarget().request()
                .put(Entity.entity("{\"key\":\"customer-auth\",\"name\":\"customer-auth\",\"path\":\"/customer-auth\","
                        + "\"unknownSetting\":true}", MediaType.APPLICATION_JSON_TYPE));

        assertEquals(400, response.getStatus());
        ErrorEntity error = readEntity(response, ErrorEntity.class);
        assertEquals("Property [unknownSetting] is not recognized as a valid property", error.getMessage());
        assertEquals(400, error.getHttpCode());
    }

    @Test
    void unsupported_media_type_is_rejected_with_a_json_error() {
        Response response = domainsTarget().request()
                .put(Entity.entity("key=customer-auth", MediaType.APPLICATION_FORM_URLENCODED_TYPE));

        assertEquals(415, response.getStatus());
        assertTrue(MediaType.APPLICATION_JSON_TYPE.isCompatible(response.getMediaType()));
        assertEquals(415, readEntity(response, ErrorEntity.class).getHttpCode());
    }
}
