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

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.JwtType;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class DefaultJWTBuilderTest {

    private static final String KEY_ID = "the-key-id";
    private RSAKey rsaJWK;
    private JWTBuilder builder;

    @BeforeEach
    void setUp() throws Exception {
        rsaJWK = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        builder = new DefaultJWTBuilder(rsaJWK.toRSAPrivateKey(), SignatureAlgorithm.RS256.getValue(), KEY_ID);
    }

    @Test
    void shouldUseJwtTypeWhenNoneSupplied() throws Exception {
        var header = SignedJWT.parse(builder.sign(payload())).getHeader();

        assertThat(header.getType()).isEqualTo(JOSEObjectType.JWT);
    }

    @Test
    void shouldUseSuppliedType() throws Exception {
        var header = SignedJWT.parse(builder.sign(idJagPayload())).getHeader();

        assertThat(header.getType().getType()).isEqualTo(JwtType.ID_JAG.getValue());
    }

    @Test
    void shouldKeepAlgorithmAndKeyIdWhenTypeIsSupplied() throws Exception {
        var typedHeader = SignedJWT.parse(builder.sign(idJagPayload())).getHeader();

        assertThat(typedHeader.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(typedHeader.getKeyID()).isEqualTo(KEY_ID);
    }

    @Test
    void shouldNotLeakSuppliedTypeToSubsequentSignatures() throws Exception {
        builder.sign(idJagPayload());

        var header = SignedJWT.parse(builder.sign(payload())).getHeader();

        assertThat(header.getType()).isEqualTo(JOSEObjectType.JWT);
    }

    @Test
    void shouldKeepClaimsWhenTypeIsSupplied() throws Exception {
        var claims = SignedJWT.parse(builder.sign(idJagPayload())).getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo("the-user");
    }

    @Test
    void shouldNotEmitTheTypeAsAClaim() throws Exception {
        var claims = SignedJWT.parse(builder.sign(idJagPayload())).getJWTClaimsSet();

        assertThat(claims.getClaims()).doesNotContainKey("type").doesNotContainKey("typ");
    }

    @Test
    void shouldSignWithIssuerConstructorWhenTypeIsSupplied() throws Exception {
        JWTBuilder withIssuer = new DefaultJWTBuilder(rsaJWK.toRSAPrivateKey(), SignatureAlgorithm.RS256.getValue(), KEY_ID, "https://am");

        var signed = SignedJWT.parse(withIssuer.sign(idJagPayload()));

        assertThat(signed.getHeader().getType().getType()).isEqualTo(JwtType.ID_JAG.getValue());
        assertThat(signed.getJWTClaimsSet().getIssuer()).isEqualTo("https://am");
    }

    private JWT payload() {
        JWT jwt = new JWT();
        jwt.setSub("the-user");
        return jwt;
    }

    private JWT idJagPayload() {
        JWT jwt = payload();
        jwt.setType(JwtType.ID_JAG);
        return jwt;
    }
}
