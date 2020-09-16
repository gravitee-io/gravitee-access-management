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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.Form;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface FormRepository extends CrudRepository<Form, String> {

    Single<List<Form>> findAll(ReferenceType referenceType, String referenceId);

    Flowable<Form> findAll(ReferenceType referenceType);

    Single<List<Form>> findByDomain(String domain);

    Single<List<Form>> findByClient(ReferenceType referenceType, String referenceId, String client);

    Maybe<Form> findByTemplate(ReferenceType referenceType, String referenceId, String template);

    Maybe<Form> findByClientAndTemplate(ReferenceType referenceType, String referenceId, String client, String template);

    Maybe<Form> findById(ReferenceType referenceType, String referenceId, String id);

}
