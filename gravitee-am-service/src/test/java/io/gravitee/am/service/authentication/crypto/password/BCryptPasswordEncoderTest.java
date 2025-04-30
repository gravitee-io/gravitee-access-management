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

package io.gravitee.am.service.authentication.crypto.password;

import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import io.gravitee.am.service.exception.InvalidPasswordException;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BCryptPasswordEncoderTest extends AbstractPasswordEncoderTest {
    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    protected PasswordEncoder getEncoder() {
        return encoder;
    }

    @Test(expected = InvalidPasswordException.class)
    public void testPassword_with_more_than_72chars_should_not_be_encoded() {
        String pwdOf73Chars = "Test123456789!654012345678901234567890123456789012345678901234567Test123456";
        getEncoder().encode(pwdOf73Chars);
    }
}
