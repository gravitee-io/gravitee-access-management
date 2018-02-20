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
import io.gravitee.am.model.login.LoginForm;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.UpdateDomain;
import io.gravitee.am.service.model.UpdateLoginForm;

import java.util.Collection;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DomainService {

    Domain findById(String id);

    Set<Domain> findAll();

    Set<Domain> findByIdIn(Collection<String> ids);

    Domain create(NewDomain domain);

    Domain update(String domainId, UpdateDomain domain);

    Domain reload(String domainId);

    Domain setMasterDomain(String domainId, boolean isMaster);

    LoginForm updateLoginForm(String domainId, UpdateLoginForm loginForm);

    void deleteLoginForm(String domainId);

    void delete(String domain);

}
