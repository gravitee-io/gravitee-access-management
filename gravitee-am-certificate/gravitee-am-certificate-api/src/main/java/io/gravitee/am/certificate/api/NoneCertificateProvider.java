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

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

class NoneCertificateProvider implements CertificateProvider {
    private final CertificateMetadata metadata;

    NoneCertificateProvider() {
        this.metadata = new CertificateMetadata();
        metadata.setMetadata(Collections.singletonMap(CertificateMetadata.DIGEST_ALGORITHM_NAME, SignatureAlgorithm.NONE.getValue()));
    }

    @Override
    public Optional<Date> getExpirationDate() {
        return Optional.empty();
    }

    @Override
    public Flowable<JWK> privateKey() {
        throw new UnsupportedOperationException("No private key for \"none\" algorithm");
    }

    @Override
    public Single<io.gravitee.am.certificate.api.Key> key() {
        throw new UnsupportedOperationException("No key for \"none\" algorithm");
    }

    @Override
    public Single<String> publicKey() {
        throw new UnsupportedOperationException("No public key for \"none\" algorithm");
    }

    @Override
    public Flowable<JWK> keys() {
        throw new UnsupportedOperationException("No keys for \"none\" algorithm");
    }

    @Override
    public String signatureAlgorithm() {
        return SignatureAlgorithm.NONE.getValue();
    }

    @Override
    public CertificateMetadata certificateMetadata() {
        return metadata;
    }
}
