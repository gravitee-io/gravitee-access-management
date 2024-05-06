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

import io.gravitee.am.common.password.PasswordSaltFormat;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class MessageDigestPasswordEncoderTest extends AbstractPasswordEncoderTest {

    private final Base64.Encoder encoder = Base64.getEncoder();
    private final MessageDigestPasswordEncoder digester;

    public MessageDigestPasswordEncoderTest(MessageDigestPasswordEncoder digester) {
        super();
        this.digester = digester;
    }

    @Override
    protected PasswordEncoder getEncoder() {
        return this.digester;
    }

    @Test
    public void testPassword_match_not_equals_dedicated_salt() {
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.DIGEST, false));
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.DIGEST, true));
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.DIGEST, false, 100));
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.DIGEST, true, 100));
    }

    @Test
    public void testPassword_match_equals_dedicated_salt() {
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.DIGEST, false));
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.DIGEST, true));
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.DIGEST, false, 100));
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.DIGEST, true, 100));
    }

    @Test
    public void testPassword_match_not_equals_dedicated_salt_appending_format() {
        Assert.assertFalse(match_equals_dedicated_salt("myPassword", "wrongPassword", PasswordSaltFormat.APPENDING, false));
        Assert.assertFalse(match_equals_dedicated_salt("myPassword", "wrongPassword", PasswordSaltFormat.APPENDING, true));
        Assert.assertFalse(match_equals_dedicated_salt("myPassword", "wrongPassword", PasswordSaltFormat.APPENDING, false, 100));
        Assert.assertFalse(match_equals_dedicated_salt("myPassword", "wrongPassword", PasswordSaltFormat.APPENDING, true, 100));
    }

    @Test
    public void testPassword_match_equals_dedicated_salt_appending_format() {
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.APPENDING, false));
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.APPENDING, true));
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.APPENDING, false, 100));
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.APPENDING, true, 100));
    }

    @Test
    public void testPassword_match_not_equals_dedicated_salt_prepending_format() {
        Assert.assertFalse(match_equals_dedicated_salt("myPassword", "wrongPassword", PasswordSaltFormat.PREPENDING, false));
        Assert.assertFalse(match_equals_dedicated_salt("myPassword", "wrongPassword", PasswordSaltFormat.PREPENDING, true));
        Assert.assertFalse(match_equals_dedicated_salt("myPassword", "wrongPassword", PasswordSaltFormat.PREPENDING, false, 100));
        Assert.assertFalse(match_equals_dedicated_salt("myPassword", "wrongPassword", PasswordSaltFormat.PREPENDING, true, 100));
    }

    @Test
    public void testPassword_match_equals_dedicated_salt_prepending_format() {
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.PREPENDING, false));
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.PREPENDING, true));
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.PREPENDING, false, 100));
        Assert.assertTrue(match_equals_dedicated_salt("myPassword", "myPassword", PasswordSaltFormat.PREPENDING, true, 100));
    }


    private boolean match_equals_dedicated_salt(String p1, String p2, String format, boolean encodingAsString) {
        return match_equals_dedicated_salt(p1, p2, format, encodingAsString, 1);
    }

    private boolean match_equals_dedicated_salt(String p1, String p2, String format, boolean encodingAsString, int rounds) {
        digester.setPasswordSaltFormat(format);
        digester.setIterationsRounds(rounds);
        byte[] salt = new byte[32];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(salt);
        if (encodingAsString) {
            final String encodedSalt = encoder.encodeToString(salt);
            String encodedPassword = digester.encode(p1, salt);
            return digester.matches(p2, encodedPassword, encodedSalt);
        } else {
            String encodedPassword = digester.encode(p1, salt);
            return digester.matches(p2, encodedPassword, salt);
        }
    }

}
