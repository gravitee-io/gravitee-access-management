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
package io.gravitee.am.management.service;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.impl.CertificateEntity;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.am.service.model.UpdateCertificate;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

public interface CertificateServiceProxy {

    Maybe<Certificate> findById(String id);

    Single<List<CertificateEntity>> findByDomainAndUse(String domain, String use);

    Single<Certificate> create(String domain, NewCertificate newCertificate, User principal);

    Single<Certificate> update(String domain, String id, UpdateCertificate updateCertificate, User principal);

    Completable delete(String certificateId, User principal);

    Single<Certificate> rotate(String domain, User principal);
}
