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
package io.gravitee.am.service;

import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CredentialService {

    Maybe<Credential> findById(String id);

    Single<List<Credential>> findByUserId(ReferenceType referenceType, String referenceId, String userId);

    Single<List<Credential>> findByUsername(ReferenceType referenceType, String referenceId, String username);

    Single<List<Credential>> findByCredentialId(ReferenceType referenceType, String referenceId, String credentialId);

    Single<Credential> create(Credential credential);

    Single<Credential> update(Credential credential);

    Completable update(ReferenceType referenceType, String referenceId, String credentialId, String userId);

    Completable delete(String id);
}
