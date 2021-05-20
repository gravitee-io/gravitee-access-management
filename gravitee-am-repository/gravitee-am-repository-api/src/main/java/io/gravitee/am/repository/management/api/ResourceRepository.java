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

import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public interface ResourceRepository extends CrudRepository<Resource, String> {

    Single<Page<Resource>> findByDomain(String domain, int page, int size);
    Single<Page<Resource>> findByDomainAndClient(String domain, String client, int page, int size);
    Flowable<Resource> findByResources(List<String> resources);
    Flowable<Resource> findByDomainAndClientAndUser(String domain, String client, String userId);
    Flowable<Resource> findByDomainAndClientAndResources(String domain, String client, List<String> resource);
    Maybe<Resource> findByDomainAndClientAndUserAndResource(String domain, String client, String userId, String resource);
}
