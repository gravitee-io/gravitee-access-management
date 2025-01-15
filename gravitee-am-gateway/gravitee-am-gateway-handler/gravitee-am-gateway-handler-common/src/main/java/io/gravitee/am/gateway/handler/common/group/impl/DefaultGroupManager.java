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
package io.gravitee.am.gateway.handler.common.group.impl;

import io.gravitee.am.dataplane.api.repository.GroupRepository;
import io.gravitee.am.gateway.handler.common.group.GroupManager;
import io.gravitee.am.model.Group;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class DefaultGroupManager implements GroupManager {

    private Single<GroupRepository> cachedRepository;

    @Override
    public Flowable<Group> findByMember(String userId) {
        return cachedRepository.flatMapPublisher(repository -> repository.findByMember(userId));
    }

    @Override
    public Flowable<Group> findByIds(List<String> ids) {
        return cachedRepository.flatMapPublisher(repository -> repository.findByIdIn(ids));
    }
}
