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
package io.gravitee.am.service.dataplane;

import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CredentialCommonService {

    Flowable<Credential> findByUsername(Domain domain, String username);

    Single<Credential> update(Domain domain, Credential credential);

    // FIXME: required to be in common until User is managing the DataPlane
    //        it may not be useful to have this common service if we provide
    //        a function into the UpdateUserNameRule to manange the credential updates.
    Flowable<Credential> findByUserId(Domain domain, String userId);
    Completable delete(Domain domain, String id, boolean enforceFactorDelete);
    Completable deleteByDomain(Domain domain);
}
