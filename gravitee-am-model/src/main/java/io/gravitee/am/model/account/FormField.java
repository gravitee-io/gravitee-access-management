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
package io.gravitee.am.model.account;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Schema(title = "Form field", description = "A single field shown on a user-facing form, such as registration.")
public class FormField {
    @Schema(description = "Identifier of the field, mapped to a user attribute.", example = "email")
    private String key;
    @Schema(description = "Label displayed for the field.", example = "Email")
    private String label;
    @Schema(description = "Input type of the field.", example = "email")
    private String type;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public static FormField getEmailField() {
        FormField formField = new FormField();
        formField.setKey("email");
        formField.setType("email");
        formField.setLabel("Email");
        return formField;
    }
}
