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

import io.gravitee.am.model.Email;
import io.gravitee.am.service.model.NewEmail;
import io.gravitee.am.service.model.UpdateEmail;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface EmailTemplateService {

    Single<List<Email>> findAll();

    Single<List<Email>> findByDomain(String domain);

    Single<List<Email>> findByDomainAndClient(String domain, String client);

    Maybe<Email> findByDomainAndTemplate(String domain, String template);

    Maybe<Email> findByDomainAndClientAndTemplate(String domain, String client, String template);

    Single<Email> create(String domain, NewEmail newEmail);

    Single<Email> create(String domain, String client, NewEmail newEmail);

    Single<Email> update(String domain, String id, UpdateEmail updateEmail);

    Single<Email> update(String domain, String client, String id, UpdateEmail updateEmail);

    Completable delete(String emailId);

}
