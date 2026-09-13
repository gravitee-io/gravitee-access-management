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
package io.gravitee.am.common.jwt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class JWTTest {

    @Test
    void shouldHaveNoTypeByDefault() {
        assertThat(new JWT().getType()).isNull();
    }

    @Test
    void shouldNotExposeTheTypeAsAClaim() {
        JWT jwt = new JWT();
        jwt.setType(JwtType.ID_JAG);

        assertThat(jwt).isEmpty();
        assertThat(jwt.getType()).isEqualTo(JwtType.ID_JAG);
    }

    @Test
    void shouldKeepTheTypeWhenCopyingAnotherJwt() {
        JWT source = new JWT();
        source.setType(JwtType.ID_JAG);
        source.setSub("the-user");

        JWT copy = new JWT(source);

        assertThat(copy.getType()).isEqualTo(JwtType.ID_JAG);
        assertThat(copy.getSub()).isEqualTo("the-user");
    }

    @Test
    void shouldHaveNoTypeWhenBuiltFromAPlainClaimsMap() {
        assertThat(new JWT(Map.of("sub", "the-user")).getType()).isNull();
    }
}
