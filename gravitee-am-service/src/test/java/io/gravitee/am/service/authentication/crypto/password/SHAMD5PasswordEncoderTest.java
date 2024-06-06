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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;

/**
 * @@author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
class SHAMD5PasswordEncoderTest extends AbstractPasswordEncoderTest {

    private MessageDigestPasswordEncoder messageDigestPasswordEncoder = new SHAMD5PasswordEncoder("SHA-256");

    @Override
    protected PasswordEncoder getEncoder() {
        return this.messageDigestPasswordEncoder;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100, 1000})
    void testPassword_match_not_equals_dedicated_salt(int rounds) {
        messageDigestPasswordEncoder.setIterationsRounds(rounds);
        byte[] salt = new byte[32];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(salt);
        String encodedPassword = messageDigestPasswordEncoder.encode("myPassword", salt);
        Assert.assertFalse(messageDigestPasswordEncoder.matches("wrongPassword", encodedPassword, salt));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100, 1000})
    void testPassword_match_equals_dedicated_salt(int rounds) {
        messageDigestPasswordEncoder.setIterationsRounds(rounds);
        byte[] salt = new byte[32];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(salt);
        String encodedPassword = messageDigestPasswordEncoder.encode("myPassword", salt);
        Assert.assertTrue(messageDigestPasswordEncoder.matches("myPassword", encodedPassword, salt));
    }

    @Test
    void testPassword_match_equals_b64_encoded() {
        String b64EncodedSalt = "Da11RaXXUaGQ39oI/f8WyXxYJ3AWDg9UECmURKKQ1vE=";
        String b64EncodedPassword = "M/sMmBh2AH+S5jkaMvhab/akUQFGHwxG+Qi7AiC9kIU=";
        String rawPassword = "Test123!";
        Assert.assertTrue(messageDigestPasswordEncoder.matches(rawPassword, b64EncodedPassword, b64EncodedSalt));
    }

    @Test
    void byDefaultIterationRoundsIsSetTo1() {
        Assert.assertEquals(1, messageDigestPasswordEncoder.iterationsRounds);
    }

    @Test
    void negativeIterationRoundsNumberShouldNotChangeTheSettings() {
        Assert.assertEquals(1, messageDigestPasswordEncoder.iterationsRounds);
        messageDigestPasswordEncoder.setIterationsRounds(-5);
        Assert.assertEquals(1, messageDigestPasswordEncoder.iterationsRounds);
    }
}
