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
package io.gravitee.am.gateway.handler.oauth2.service.device;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author GraviteeSource Team
 */
class UserCodeGeneratorTest {

    @Test
    void shouldGenerateEightCharactersFromTheConsonantAlphabet() {
        for (int i = 0; i < 500; i++) {
            String code = UserCodeGenerator.generate();
            assertEquals(8, code.length());
            for (char c : code.toCharArray()) {
                assertTrue(UserCodeGenerator.ALPHABET.indexOf(c) >= 0, "unexpected character '" + c + "' in " + code);
            }
        }
    }

    @Test
    void shouldGenerateDistinctCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            codes.add(UserCodeGenerator.generate());
        }
        assertTrue(codes.size() > 190, "generator produced too many collisions: " + codes.size());
    }

    @Test
    void shouldFormatInTwoGroupsOfFour() {
        assertEquals("BCDF-GHJK", UserCodeGenerator.format("BCDFGHJK"));
    }

    @Test
    void shouldFormatNullAsNull() {
        assertNull(UserCodeGenerator.format(null));
    }

    @Test
    void shouldNormalizeCaseAndSeparators() {
        assertEquals("BCDFGHJK", UserCodeGenerator.normalize("bcdf-ghjk"));
        assertEquals("BCDFGHJK", UserCodeGenerator.normalize(" BCDF GHJK "));
        assertEquals("BCDFGHJK", UserCodeGenerator.normalize("BCDFGHJK"));
    }

    @Test
    void shouldNormalizeNullAsNull() {
        assertNull(UserCodeGenerator.normalize(null));
    }
}
