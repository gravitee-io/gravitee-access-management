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

import java.security.SecureRandom;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MD5PasswordEncoderTest {

    private MessageDigestPasswordEncoder messageDigestPasswordEncoder = new MD5PasswordEncoder();

    @Test
    public void testPassword_match_not_equals() {
        String encodedPassword = messageDigestPasswordEncoder.encode("myPassword");
        Assert.assertFalse(messageDigestPasswordEncoder.matches("wrongPassword", encodedPassword));
    }

    @Test
    public void testPassword_match_equals() {
        String encodedPassword = messageDigestPasswordEncoder.encode("myPassword");
        Assert.assertTrue(messageDigestPasswordEncoder.matches("myPassword", encodedPassword));
    }

    @Test
    public void testPassword_match_not_equals_dedicated_salt() {
        byte[] salt = new byte[32];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(salt);
        String encodedPassword = messageDigestPasswordEncoder.encode("myPassword", salt);
        Assert.assertFalse(messageDigestPasswordEncoder.matches("wrongPassword", encodedPassword, salt));
    }

    @Test
    public void testPassword_match_equals_dedicated_salt() {
        byte[] salt = new byte[32];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(salt);
        String encodedPassword = messageDigestPasswordEncoder.encode("myPassword", salt);
        Assert.assertTrue(messageDigestPasswordEncoder.matches("myPassword", encodedPassword, salt));
    }
}
