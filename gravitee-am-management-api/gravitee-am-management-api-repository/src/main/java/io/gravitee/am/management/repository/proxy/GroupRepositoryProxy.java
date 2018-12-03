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

import io.gravitee.am.model.Group;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.GroupRepository;
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
public class GroupRepositoryProxy extends AbstractProxy<GroupRepository> implements GroupRepository {

    @Override
    public Single<Page<Group>> findByDomain(String domain, int page, int size) {
        return target.findByDomain(domain, page, size);
    }

    @Override
    public Single<List<Group>> findByMember(String memberId) {
        return target.findByMember(memberId);
    }

    @Override
    public Single<List<Group>> findByDomain(String domain) {
        return target.findByDomain(domain);
    }

    @Override
    public Single<List<Group>> findByIdIn(List<String> ids) {
        return target.findByIdIn(ids);
    }

    @Override
    public Maybe<Group> findByDomainAndName(String domain, String groupName) {
        return target.findByDomainAndName(domain, groupName);
    }

    @Override
    public Maybe<Group> findById(String id) {
        return target.findById(id);
    }

    @Override
    public Single<Group> create(Group item) {
        return target.create(item);
    }

    @Override
    public Single<Group> update(Group item) {
        return target.update(item);
    }

    @Override
    public Completable delete(String id) {
        return target.delete(id);
    }
}
