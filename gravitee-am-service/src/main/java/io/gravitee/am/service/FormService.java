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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Form;
import io.gravitee.am.service.model.NewForm;
import io.gravitee.am.service.model.UpdateForm;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface FormService {

    Single<List<Form>> findByDomain(String domain);

    Single<List<Form>> findByDomainAndClient(String domain, String client);

    Maybe<Form> findByDomainAndTemplate(String domain, String template);

    Maybe<Form> findByDomainAndClientAndTemplate(String domain, String client, String template);

    Single<Form> create(String domain, NewForm form, User principal);

    Single<Form> create(String domain, String client, NewForm form, User principal);

    Single<Form> update(String domain, String id, UpdateForm form, User principal);

    Single<Form> update(String domain, String client, String id, UpdateForm form, User principal);

    Completable delete(String pageId, User principal);

    default Single<Form> create(String domain, NewForm form) {
        return create(domain, form, null);
    }

    default Single<Form> create(String domain, String client, NewForm form) {
        return create(domain, client, form, null);
    }

    default Single<Form> update(String domain, String id, UpdateForm form) {
        return update(domain, id, form, null);
    }

    default Single<Form> update(String domain, String client, String id, UpdateForm form) {
        return update(domain, client, id, form);
    }

    default Completable delete(String pageId) {
        return delete(pageId);
    }

}
