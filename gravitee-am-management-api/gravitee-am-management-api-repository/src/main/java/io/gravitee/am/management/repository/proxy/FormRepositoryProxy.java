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
package io.gravitee.am.management.repository.proxy;

import io.gravitee.am.model.Form;
import io.gravitee.am.repository.management.api.FormRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class FormRepositoryProxy extends AbstractProxy<FormRepository> implements FormRepository {

    @Override
    public Single<List<Form>> findByDomain(String domain) {
        return target.findByDomain(domain);
    }

    @Override
    public Single<List<Form>> findByDomainAndClient(String domain, String client) {
        return target.findByDomainAndClient(domain, client);
    }

    @Override
    public Maybe<Form> findByDomainAndTemplate(String domain, String template) {
        return target.findByDomainAndTemplate(domain, template);
    }

    @Override
    public Maybe<Form> findByDomainAndClientAndTemplate(String domain, String client, String template) {
        return target.findByDomainAndClientAndTemplate(domain, client, template);
    }

    @Override
    public Maybe<Form> findById(String id) {
        return target.findById(id);
    }

    @Override
    public Single<Form> create(Form item) {
        return target.create(item);
    }

    @Override
    public Single<Form> update(Form item) {
        return target.update(item);
    }

    @Override
    public Completable delete(String id) {
        return target.delete(id);
    }
}
