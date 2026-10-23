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
package io.gravitee.am.service.validators.authorizationdetailtype;

import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.service.validators.Validator;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

import static io.gravitee.am.model.oauth2.AuthorizationDetailType.DESCRIPTION_MAX_LENGTH;
import static io.gravitee.am.model.oauth2.AuthorizationDetailType.NAME_MAX_LENGTH;
import static io.gravitee.am.model.oauth2.AuthorizationDetailType.TYPE_MAX_LENGTH;

/**
 * @author GraviteeSource Team
 */
@Component
public class AuthorizationDetailTypeValidator implements Validator<AuthorizationDetailType, Optional<String>> {

    public static final String TYPE_CHARACTERS_MESSAGE = "Type must contain only visible ASCII characters (%x21-7E)";

    private static final Pattern TYPE_PATTERN = Pattern.compile(AuthorizationDetailType.TYPE_PATTERN);

    private final AuthorizationDetailTypeSchemaValidator schemaValidator = new AuthorizationDetailTypeSchemaValidator();

    @Override
    public Optional<String> validate(AuthorizationDetailType element) {
        return validateType(element.getType())
                .or(() -> validateName(element.getName()))
                .or(() -> validateDescription(element.getDescription()))
                .or(() -> validateExpiresIn(element.getExpiresIn()))
                .or(() -> element.getSchema() == null ? Optional.empty() : schemaValidator.validate(element.getType(), element.getSchema()));
    }

    private static Optional<String> validateType(String type) {
        if (type == null || type.isEmpty()) {
            return Optional.of("Type is required");
        }
        if (type.length() > TYPE_MAX_LENGTH) {
            return Optional.of("Type must be at most " + TYPE_MAX_LENGTH + " characters");
        }
        if (!TYPE_PATTERN.matcher(type).matches()) {
            return Optional.of(TYPE_CHARACTERS_MESSAGE);
        }
        return Optional.empty();
    }

    private static Optional<String> validateName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.of("Name is required");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            return Optional.of("Name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return Optional.empty();
    }

    private static Optional<String> validateDescription(String description) {
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            return Optional.of("Description must be at most " + DESCRIPTION_MAX_LENGTH + " characters");
        }
        return Optional.empty();
    }

    private static Optional<String> validateExpiresIn(Integer expiresIn) {
        if (expiresIn != null && expiresIn < 1) {
            return Optional.of("Expires in must be greater than 0");
        }
        return Optional.empty();
    }
}
