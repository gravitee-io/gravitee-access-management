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
package io.gravitee.am.certificate.api;

import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.model.jose.JWK;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.experimental.UtilityClass;

import java.security.InvalidKeyException;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

@UtilityClass
public class CertificateProviders {

    public static CertificateProvider createNoneCertificateProvider(){
        return new NoneCertificateProvider();
    }

    public static CertificateProvider createShaCertificateProvider(String singingKeyId, String signingKeySecret) throws InvalidKeyException {
        byte[] keySecretBytes = signingKeySecret.getBytes();
        java.security.Key key = Keys.hmacShaKeyFor(keySecretBytes);
        SignatureAlgorithm signatureAlgorithm = Keys.hmacShaSignatureAlgorithmFor(keySecretBytes);
        io.gravitee.am.certificate.api.Key certificateKey = new DefaultKey(singingKeyId, key);

        // create default certificate provider
        CertificateMetadata certificateMetadata = new CertificateMetadata();
        certificateMetadata.setMetadata(Collections.singletonMap(CertificateMetadata.DIGEST_ALGORITHM_NAME, signatureAlgorithm.getDigestName()));

        return new io.gravitee.am.certificate.api.CertificateProvider() {
            @Override
            public Optional<Date> getExpirationDate() {
                return Optional.empty();
            }

            @Override
            public Single<Key> key() {
                return Single.just(certificateKey);
            }

            @Override
            public Flowable<JWK> privateKey() {
                return null;
            }

            @Override
            public Single<String> publicKey() {
                return null;
            }

            @Override
            public Flowable<JWK> keys() {
                return null;
            }

            @Override
            public String signatureAlgorithm() {
                return signatureAlgorithm.getValue();
            }

            @Override
            public CertificateMetadata certificateMetadata() {
                return certificateMetadata;
            }
        };
    }
}
