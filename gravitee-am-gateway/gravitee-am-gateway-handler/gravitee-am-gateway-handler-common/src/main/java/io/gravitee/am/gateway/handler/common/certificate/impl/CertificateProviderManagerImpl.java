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
package io.gravitee.am.gateway.handler.common.certificate.impl;

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateProviderManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateProviderManagerImpl implements CertificateProviderManager {

    private ConcurrentMap<String, CertificateProvider> certificates = new ConcurrentHashMap<>();

    void addCertificate(String id, CertificateProvider certificateProvider) {
        certificates.put(id, certificateProvider);
    }

    void removeCertificate(String id) {
        certificates.remove(id);
    }

    @Override
    public CertificateProvider getCertificate(String id) {
        return certificates.get(id);
    }
}
