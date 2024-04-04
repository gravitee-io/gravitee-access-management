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

package io.gravitee.am.service;

import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.InvalidPasswordException;
import io.gravitee.am.service.validators.password.PasswordSettingsStatus;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface PasswordService {

    default boolean isValid(String password) {
        return isValid(password, null, null);
    }

    default boolean isValid(String password, PasswordPolicy passwordPolicy, User user) {
        try {
            validate(password, passwordPolicy, user);
            return true;
        } catch (InvalidPasswordException e) {
            return false;
        }
    }

    void validate(String password, PasswordPolicy passwordPolicy, User user);
    PasswordSettingsStatus evaluate(String password, PasswordPolicy passwordPolicy, User user);

    boolean checkAccountPasswordExpiry(User user, PasswordPolicy passwordPolicy);

}
