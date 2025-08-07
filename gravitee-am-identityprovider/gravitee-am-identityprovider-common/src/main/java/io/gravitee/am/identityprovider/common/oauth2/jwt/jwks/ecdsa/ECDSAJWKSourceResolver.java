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
package io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.ecdsa;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.util.X509CertUtils;
import io.gravitee.am.identityprovider.api.oidc.jwt.JWKSourceResolver;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.Collections;

public class ECDSAJWKSourceResolver<C extends SecurityContext> implements JWKSourceResolver<C> {

    private final JWK jwk;

    public ECDSAJWKSourceResolver(String publicKey) {
        X509Certificate cert = X509CertUtils.parse(publicKey);
        if(cert == null){
            throw new IllegalArgumentException("Invalid certificate");
        }
        jwk = parse(cert);

    }

    public ECDSAJWKSourceResolver(ECPublicKey publicKey) {
        jwk = parse(publicKey);
    }

    @Override
    public JWKSource<C> resolve() {
        return new ImmutableJWKSet<>(new JWKSet(jwk));
    }


    private static ECKey parse(final X509Certificate cert) {
        if (!(cert.getPublicKey() instanceof ECPublicKey)) {
            throw new IllegalStateException("The public key of the X.509 certificate is not EC");
        }
        ECPublicKey publicKey = (ECPublicKey) cert.getPublicKey();

        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            Curve curve = Curve.forECParameterSpec(publicKey.getParams());

            return new ECKey.Builder(curve, publicKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .x509CertChain(Collections.singletonList(Base64.encode(cert.getEncoded())))
                    .x509CertSHA256Thumbprint(Base64URL.encode(sha256.digest(cert.getEncoded())))
                    .build();
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new IllegalStateException("Couldn't encode EC key x5c/x5t parameter: " + e.getMessage(), e);
        }
    }

    private static ECKey parse(final ECPublicKey publicKey) {
        Curve curve = Curve.forECParameterSpec(publicKey.getParams());
        return new ECKey.Builder(curve, publicKey)
                .keyUse(KeyUse.SIGNATURE)
                .build();
    }
}
