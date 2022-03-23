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
package io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.jwt.DefaultJWTBuilder;
import io.gravitee.am.jwt.JWTBuilder;

import java.security.InvalidKeyException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.WEBAUTHN_REDIRECT_URI;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
interface AbstractWebAuthnTest {
    String  REDIRECT_URI = "http://redirect.com/app";

    default String createToken() throws InvalidKeyException, JOSEException {
        final RSAKey rsaJWK = new RSAKeyGenerator(2048)
                .keyID("123")
                .generate();
        final RSAPrivateKey rsaPrivateKey = rsaJWK.toRSAPrivateKey();
        final JWTBuilder jwtBuilder = new DefaultJWTBuilder(rsaPrivateKey, SignatureAlgorithm.RS256.getValue(), rsaJWK.getKeyID());
        return jwtBuilder.sign(createJWT());
    }

    default JWT createJWT(){
        final Map<String, Object> claims = Map.of(
                Claims.sub, "userId",
                Claims.aud, "clientId",
                WEBAUTHN_REDIRECT_URI, "http://redirect.com/app"
        );

        return new JWT(claims);
    }
}
