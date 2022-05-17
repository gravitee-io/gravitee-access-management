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

package io.gravitee.am.service.impl.user.activity.utils;

import io.gravitee.am.service.impl.user.activity.configuration.UserActivityConfiguration.Algorithm;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HashedKeyUtilsTest {

    @Test
    public void must_return_null_due_to_null_userId() {
        assertNull(HashedKeyUtils.computeHash(null, null, null));
    }

    @Test
    public void must_return_user_id_if_algorithm_is_null() {
        assertNull(HashedKeyUtils.computeHash(null, "user-id", null));
    }

    @Test
    public void must_return_hash_sha256_without_salt() {
        assertNotNull(HashedKeyUtils.computeHash(Algorithm.SHA256, "user-id", null));
    }

    @Test
    public void must_return_plain_user_id_without_salt() {
        assertEquals("user-id", HashedKeyUtils.computeHash(Algorithm.NONE, "user-id", null));
    }

    @Test
    public void must_return_hash_sha512_without_salt() {
        assertNotNull(HashedKeyUtils.computeHash(Algorithm.SHA512, "user-id", null));
    }

    @Test
    public void must_return_hash_sha256_with_salt() {
        assertEquals(
                "Pa6CsP3KeCeENFY9tUsMFKyv8QMlaUyMGGw8OxcFVu3",
                HashedKeyUtils.computeHash(Algorithm.SHA256, "user-id", "xXxGr4v1Te3oI0xXx")
        );
    }

    @Test
    public void must_return_hash_sha512_with_salt() {
        assertEquals(
                "QK.H/Z4k/lEQbJJU8eTa6nmS6qrh/61xI5E1vrSCi6lHXF1Y7PTDOX8SQ56hNhwGTRLA2rxW696OwKCOZIAU21",
                HashedKeyUtils.computeHash(Algorithm.SHA512, "user-id", "xXxGr4v1Te3I0xXx")
        );
    }

}
