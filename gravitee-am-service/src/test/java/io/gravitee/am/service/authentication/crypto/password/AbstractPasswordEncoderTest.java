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
public abstract class AbstractPasswordEncoderTest {

    protected abstract PasswordEncoder getEncoder();


    @Test
    public void testPassword_match_not_equals() {
        String encodedPassword = getEncoder().encode("myPassword");
        Assert.assertFalse(getEncoder().matches("wrongPassword", encodedPassword));
    }

    @Test
    public void testPassword_match_equals() {
        String encodedPassword = getEncoder().encode("myPassword");
        Assert.assertTrue(getEncoder().matches("myPassword", encodedPassword));
    }
}