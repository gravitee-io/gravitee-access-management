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
package io.gravitee.am.gateway.certificate.dummy;

import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.certificate.api.Key;
import io.gravitee.am.model.jose.JWK;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.Date;
import java.util.Optional;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultProvider implements CertificateProvider {

    private final CertificateMetadata certificateMetadata;
    private final Key certificateKey;

    public DefaultProvider(CertificateMetadata certificateMetadata, Key certificateKey) {
        this.certificateMetadata = certificateMetadata;
        this.certificateKey = certificateKey;
    }

    @Override
    public Optional<Date> getExpirationDate() {
        return Optional.empty();
    }

    @Override
    public Flowable<JWK> privateKey() {
        return null;
    }

    @Override
    public Single<io.gravitee.am.certificate.api.Key> key() {
        return Single.just(certificateKey);
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
        return "HS256";
    }

    @Override
    public CertificateMetadata certificateMetadata() {
        return certificateMetadata;
    }

    @Override
    public String toString() {
        return "DefaultProvider";
    }
}
