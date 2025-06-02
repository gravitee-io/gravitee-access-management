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

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PBKDF2PasswordEncoderTest extends AbstractPasswordEncoderTest {
    private PBKDF2PasswordEncoder encoder = new PBKDF2PasswordEncoder(16, 300000, "PBKDF2WithHmacSHA256");

    @Override
    protected PasswordEncoder getEncoder() {
        return encoder;
    }

    @Test
    public void testPassword_match_not_equals_with_salt() {
        String hash = "v5EFuaKK7l26tMEt/vNs3A==";
        String encodedPassword = "xSnKPp1Zg+IOPhvZmb056iAav1nd+qtCs1OQQ0o/nvA=";
        Assert.assertFalse(getEncoder().matches("wrongPassword", encodedPassword, hash));
    }

    @Test
    public void testPassword_match_equals() {
        String hash = "v5EFuaKK7l26tMEt/vNs3A==";
        String encodedPassword = "xSnKPp1Zg+IOPhvZmb056iAav1nd+qtCs1OQQ0o/nvA=";
        Assert.assertTrue(getEncoder().matches("password", encodedPassword, hash));
    }

}