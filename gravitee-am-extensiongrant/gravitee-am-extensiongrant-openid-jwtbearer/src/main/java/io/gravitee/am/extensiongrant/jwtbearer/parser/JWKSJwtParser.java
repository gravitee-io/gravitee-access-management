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
package io.gravitee.am.extensiongrant.jwtbearer.parser;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.jwt.SignatureException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.jwt.JWTParser;

import java.net.URL;
import java.text.ParseException;
import java.util.List;

public class JWKSJwtParser implements JWTParser {

    private final JWKSource<SecurityContext> jwkSource;

    public JWKSJwtParser(String jwksUrl) throws Exception {
        this.jwkSource = JWKSourceBuilder.create(new URL(jwksUrl)).build();
    }

    @Override
    public JWT parse(String payload) {
        SignedJWT signedJWT = parseToken(payload);
        String keyId = extractKeyId(signedJWT);
        JWK jsonWebKey = selectMatchingKey(keyId);

        try {
            JWSVerifier verifier = createVerifier(jsonWebKey);

            if (!signedJWT.verify(verifier)) {
                throw new InvalidGrantException("Token's signature is invalid");
            }

            return new JWT(signedJWT.getPayload().toJSONObject());
        } catch (JOSEException e) {
            throw new SignatureException("An error occurs while parsing JWT token", e);
        }
    }

    private static SignedJWT parseToken(final String payload) {
        try {
            return SignedJWT.parse(payload);
        } catch (ParseException e) {
            throw new SignatureException("Unabled to parse token", e);
        }
    }

    private static String extractKeyId(final SignedJWT signedJWT) {
        return signedJWT.getHeader().getKeyID();
    }

    private JWK selectMatchingKey(final String keyId) {
        try {
            JWKSelector jwkSelector = new JWKSelector(new JWKMatcher.Builder()
                    .keyID(keyId)
                    .build());

            List<JWK> jwks = jwkSource.get(jwkSelector, null);
            if (jwks.isEmpty()) {
                throw new SignatureException("No matching key found for kid: " + keyId);
            }

            return jwks.get(0);
        } catch (KeySourceException e) {
            throw new SignatureException("Unable to retreive matching jwks keys", e);
        }
    }

    private JWSVerifier createVerifier(JWK jwk) throws JOSEException {
        if (jwk instanceof RSAKey) {
            return new RSASSAVerifier((RSAKey) jwk);
        } else if (jwk instanceof ECKey) {
            return new ECDSAVerifier((ECKey) jwk);
        } else {
            throw new SignatureException("Unsupported key type: " + jwk.getKeyType());
        }
    }
}