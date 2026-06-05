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
import lombok.Getter;
import lombok.Setter;

/**
 * One criterion for resolving an external JWT subject to a domain user.
 * The domain user attribute is matched against the value produced by evaluating the EL expression
 * with the validated subject token claims in context (variable {@code token}).
 *
 * @see TrustedIssuer#getUserBindingCriteria()
 */
@Getter
@Setter
@Schema(title = "User binding criterion", description = "A single rule that matches a domain user attribute " +
        "against a value derived from the external token claims.")
public class UserBindingCriterion {

    @Schema(description = "Domain user attribute used for the lookup. Must match a field supported by the user " +
            "repository search.", example = "emails.value")
    private String attribute;

    @Schema(description = "Expression evaluated against the validated token claims (variable \"token\") to " +
            "produce the value to match.", example = "{#token['email']}")
    private String expression;
}
