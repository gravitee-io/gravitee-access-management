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

package io.gravitee.am.service.validators.password.impl;

import io.gravitee.am.service.exception.InvalidPasswordException;
import io.gravitee.am.service.validators.password.PasswordValidator;

import static java.util.Objects.isNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConsecutiveCharacterPasswordValidator implements PasswordValidator {

    private static final String ERROR_MESSAGE = "invalid max consecutive letters";
    private static final InvalidPasswordException INVALID_PASSWORD_EXCEPTION = InvalidPasswordException.of(ERROR_MESSAGE, ERROR_KEY);

    private final Integer maxConsecutiveLetters;

    public ConsecutiveCharacterPasswordValidator(Integer maxConsecutiveLetters) {
        this.maxConsecutiveLetters = maxConsecutiveLetters;
    }

    @Override
    public Boolean validate(String password) {
        return isNull(maxConsecutiveLetters) || maxConsecutiveLetters < 1 ||
                !isOverMaxConsecutiveLetters(password, maxConsecutiveLetters);
    }

    /**
     * Test if any character is repeated consecutively more than the giver max number
     * str="aaabb", max=3 -> true
     * str="aaabb", max=2 -> false
     */
    private static boolean isOverMaxConsecutiveLetters(String str, int max) {
        int len = str.length();
        for (int i = 0; i < len; i++) {
            int cur_count = 1;
            for (int j = i + 1; j < len; j++) {
                if (str.charAt(i) != str.charAt(j)) {
                    break;
                }
                cur_count++;
            }

            if (cur_count > max) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InvalidPasswordException getCause() {
        return INVALID_PASSWORD_EXCEPTION;
    }
}
