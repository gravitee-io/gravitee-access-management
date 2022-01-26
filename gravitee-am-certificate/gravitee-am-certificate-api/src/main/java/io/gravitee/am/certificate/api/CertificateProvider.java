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

import io.gravitee.am.model.jose.JWK;
import io.reactivex.Flowable;
import io.reactivex.Single;

import javax.swing.text.html.Option;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CertificateProvider {

    Optional<Date> getExpirationDate();

    Single<Key> key();

    Single<String> publicKey();

    Flowable<JWK> privateKey();

    Flowable<JWK> keys();

    CertificateMetadata certificateMetadata();

    String signatureAlgorithm();

    default Certificate certificate() {
        return null;
    }

    default Single<List<CertificateKey>> publicKeys() {
        return Single.just(Collections.emptyList());
    }
}
