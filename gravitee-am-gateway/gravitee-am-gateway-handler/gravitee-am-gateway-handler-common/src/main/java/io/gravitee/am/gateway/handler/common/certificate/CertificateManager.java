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
package io.gravitee.am.gateway.handler.common.certificate;

import io.gravitee.am.common.exception.oauth2.TemporarilyUnavailableException;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.service.Service;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CertificateManager extends io.gravitee.am.certificate.api.CertificateManager, Service {

    Maybe<CertificateProvider> get(String id);

    Maybe<CertificateProvider> findByAlgorithm(String algorithm);

    Collection<CertificateProvider> providers();

    CertificateProvider defaultCertificateProvider();

    CertificateProvider noneAlgorithmCertificateProvider();

    default Single<CertificateProvider> getClientCertificateProvider(Client client, boolean fallbackToHmacSignature) {
        if (client.getCertificate() == null) {
            return Single.just(defaultCertificateProvider());
        }

        return get(client.getCertificate())
                .switchIfEmpty(Maybe.defer(() ->
                        fallbackCertificateProvider()
                                .doOnSuccess(fallback -> {
                                    Logger logger = LoggerFactory.getLogger(this.getClass());
                                    String fallbackCertificateId = fallback.getCertificateInfo().certificateId();
                                    logger.warn("Certificate: {} not loaded, using: {} as fallback", client.getCertificate(), fallbackCertificateId);
                                })
                                .switchIfEmpty(
                                    Maybe.defer(() ->
                                        fallbackToHmacSignature
                                                ? Maybe.just(defaultCertificateProvider())
                                                : Maybe.empty()
                                    ).doOnSuccess(defaultCertificateProvider -> {
                                        Logger logger = LoggerFactory.getLogger(this.getClass());
                                        logger.warn("Certificate: {} not loaded, using default certificate as fallback", client.getCertificate());
                                    })
                                )

                ))
                .switchIfEmpty(Single.error(new TemporarilyUnavailableException("The certificate cannot be loaded")));
    }

    Maybe<CertificateProvider> fallbackCertificateProvider();
}
