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

import io.gravitee.common.service.Service;
import io.reactivex.Maybe;

import java.util.Collection;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CertificateManager extends Service {

    Maybe<CertificateProvider> get(String id);

    Maybe<CertificateProvider> findByAlgorithm(String algorithm);

    Maybe<CertificateProvider> findByDomainAndId(String domain, String id);

    Collection<CertificateProvider> providers();

    CertificateProvider defaultCertificateProvider();

    CertificateProvider noneAlgorithmCertificateProvider();
}
