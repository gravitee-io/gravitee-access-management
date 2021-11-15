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
package io.gravitee.am.service.validators.accountsettings;

import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.account.FormField;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AccountSettingsValidatorImpl implements AccountSettingsValidator {

    public static final String EMAIL = "email";
    public static final String USERNAME = "username";
    private static final HashSet<String> AUTHORIZED_FIELDS = new HashSet<>(asList(EMAIL, USERNAME));

    @Override
    public Boolean validate(AccountSettings element) {
        return !hasInvalidResetPasswordFields(element);
    }

    private boolean hasInvalidResetPasswordFields(AccountSettings settings) {
        if (settings != null && settings.isResetPasswordCustomForm()) {
            final List<FormField> fields = Optional.ofNullable(settings.getResetPasswordCustomFormFields())
                    .orElse(List.of());
            return fields.stream().anyMatch(field -> !AUTHORIZED_FIELDS.contains(field.getKey()));
        }
        return false;
    }
}
