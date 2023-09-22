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
package io.gravitee.am.gateway.handler.scim.service;

import io.gravitee.am.common.scim.filter.Filter;
import io.gravitee.am.gateway.handler.scim.model.Group;
import io.gravitee.am.gateway.handler.scim.model.ListResponse;
import io.gravitee.am.gateway.handler.scim.model.PatchOp;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface GroupService {

    Single<ListResponse<Group>> list(Filter filter, int page, int size, String baseUrl);

    Flowable<Group> findByMember(String memberId);

    Maybe<Group> get(String groupId, String baseUrl);

    Single<Group> create(Group group, String baseUrl, io.gravitee.am.identityprovider.api.User principal);

    Single<Group> update(String groupId, Group group, String baseUrl, io.gravitee.am.identityprovider.api.User principal);

    Single<Group> patch(String groupId, PatchOp patchOp, String baseUrl, io.gravitee.am.identityprovider.api.User principal);

    Completable delete(String groupId, io.gravitee.am.identityprovider.api.User principal);
}
