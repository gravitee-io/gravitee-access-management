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
public class SHAPasswordEncoderTest {

    private MessageDigestPasswordEncoder messageDigestPasswordEncoder = new SHAPasswordEncoder("SHA-512");

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

    @Test
    public void testPassword_match_equals_b64_encoded() {
        String b64EncodedSalt = "MKZTPrdEdt9WKjUHf5Q67j5G1lhIOlVZTwyYK0ivxKscE5Y8YY+8CUUaAEJDzOhD8SyzhdsBgsLbBD8oUzkLng==";
        String b64EncodedPassword = "lvrw0DwkxBJEfdwGwEGA+brugO/dz9omuG3rhEs3oDNHqpuHMyt6G/mwPXnRGtJTv46/uttYcjvWpT0LNIrNWw==";
        String rawPassword = "hello";
        Assert.assertTrue(messageDigestPasswordEncoder.matches(rawPassword, b64EncodedPassword, b64EncodedSalt));
    }
}
