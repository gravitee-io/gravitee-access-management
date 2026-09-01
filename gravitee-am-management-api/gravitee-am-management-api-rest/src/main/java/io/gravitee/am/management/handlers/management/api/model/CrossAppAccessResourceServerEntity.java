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
package io.gravitee.am.management.handlers.management.api.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A resource server an application of this security domain may be mapped to, flattened across the
 * trusted domains that expose it.
 *
 * @author GraviteeSource Team
 */
@Schema(title = "Cross App Access resource server",
        description = "A resource server an application may request an ID-JAG for.")
public record CrossAppAccessResourceServerEntity(
        @Schema(description = "Identifier of the trusted domain exposing this resource server.")
        String trustDomainId,
        @Schema(description = "Name of the trusted domain exposing this resource server.")
        String trustDomainName,
        @Schema(description = "Identifier of the resource server, stable across a rename.")
        String resourceServerId,
        @Schema(description = "Label the resource server is known by.")
        String name,
        @Schema(description = "Resource identifier of the resource server, carried by the \"resource\" claim.")
        String resource) {
}
