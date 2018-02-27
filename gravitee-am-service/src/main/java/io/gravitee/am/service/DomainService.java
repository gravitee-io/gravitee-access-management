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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.model.login.LoginForm;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.UpdateDomain;
import io.gravitee.am.service.model.UpdateLoginForm;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.Collection;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DomainService {

    Maybe<Domain> findById(String id);

    Single<Set<Domain>> findAll();

    Single<Set<Domain>> findByIdIn(Collection<String> ids);

    Single<Domain> create(NewDomain domain);

    Single<Domain> update(String domainId, UpdateDomain domain);

    Single<Domain> reload(String domainId);

    Single<Domain> setMasterDomain(String domainId, boolean isMaster);

    Single<LoginForm> updateLoginForm(String domainId, UpdateLoginForm loginForm);

    Single<Domain> deleteLoginForm(String domainId);

    Single<Irrelevant> delete(String domain);

}
