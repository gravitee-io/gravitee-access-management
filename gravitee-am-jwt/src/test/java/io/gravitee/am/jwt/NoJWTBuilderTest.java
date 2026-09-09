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
package io.gravitee.am.jwt;

import com.nimbusds.jwt.PlainJWT;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.JwtType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class NoJWTBuilderTest {

    private final JWTBuilder builder = new NoJWTBuilder();

    @Test
    void shouldNotSetTypeWhenNoneSupplied() throws Exception {
        var header = PlainJWT.parse(builder.sign(payload())).getHeader();

        assertThat(header.getType()).isNull();
    }

    @Test
    void shouldUseSuppliedType() throws Exception {
        var header = PlainJWT.parse(builder.sign(idJagPayload())).getHeader();

        assertThat(header.getType().getType()).isEqualTo(JwtType.ID_JAG.getValue());
    }

    @Test
    void shouldKeepSerializationUnchangedWhenNoTypeSupplied() {
        assertThat(builder.sign(payload())).isEqualTo("eyJhbGciOiJub25lIn0.eyJzdWIiOiJ0aGUtdXNlciJ9.");
    }

    private JWT idJagPayload() {
        JWT jwt = payload();
        jwt.setType(JwtType.ID_JAG);
        return jwt;
    }

    private JWT payload() {
        JWT jwt = new JWT();
        jwt.setSub("the-user");
        return jwt;
    }
}
