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
package io.gravitee.am.identityprovider.azure.jwt.jwks.rsa;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.util.X509CertUtils;
import io.gravitee.am.identityprovider.azure.jwt.jwks.JWKSourceResolver;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RSAJWKSourceResolver<C extends SecurityContext> implements JWKSourceResolver<C> {

    private final JWK jwk;

    public RSAJWKSourceResolver(String publicKey) {
        X509Certificate cert = X509CertUtils.parse(publicKey);
        jwk = parse(cert);
    }

    @Override
    public JWKSource<C> resolve() {
        return new ImmutableJWKSet<>(new JWKSet(jwk));
    }

    private static RSAKey parse(final X509Certificate cert) {
        if (! (cert.getPublicKey() instanceof RSAPublicKey)) {
            throw new IllegalStateException("The public key of the X.509 certificate is not RSA");
        }

        RSAPublicKey publicKey = (RSAPublicKey)cert.getPublicKey();

        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

            return new RSAKey.Builder(publicKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .x509CertChain(Collections.singletonList(Base64.encode(cert.getEncoded())))
                    .x509CertSHA256Thumbprint(Base64URL.encode(sha256.digest(cert.getEncoded())))
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Couldn't encode x5t parameter: " + e.getMessage(), e);
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException("Couldn't encode x5c parameter: " + e.getMessage(), e);
        }
    }
}
