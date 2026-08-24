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
package io.gravitee.am.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Declares one additional attribute a reporter exports alongside its regular audit payload. The
 * expression is read from the audit context at report time and its value is written under
 * {@code exportedName}.
 *
 * @author GraviteeSource Team
 */
@Schema(title = "Reporter attribute mapping", description = "Exports one additional attribute, read " +
        "from the audit context by expression, under a chosen field name.")
public record ReporterAttributeMapping(
        @Schema(description = "Expression evaluated against the audit context.",
                example = "{#context.attributes['user'].additionalInformation['sub']}")
        String expression,

        @Schema(description = "The name the evaluated value takes on the exported payload.",
                example = "user_sub")
        String exportedName) {
}
