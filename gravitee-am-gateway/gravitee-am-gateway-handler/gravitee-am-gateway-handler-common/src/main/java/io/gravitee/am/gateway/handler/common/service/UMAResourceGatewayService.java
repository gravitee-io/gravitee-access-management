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

package io.gravitee.am.gateway.handler.common.service;

import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.gravitee.am.service.model.NewResource;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

public interface UMAResourceGatewayService {
    Single<Page<Resource>> findAll(int page, int size);
    Flowable<Resource> findByResources(List<String> resourceIds);
    Flowable<Resource> listByClientAndUser(String client, String userId);
    Flowable<Resource> findByClientAndResources(String client, List<String> resourceIds);
    Maybe<Resource> findByClientAndUserAndResource(String client, String userId, String resourceId);
    Single<Resource> create(NewResource newResource, String client, String userId);
    Single<Resource> update(NewResource newResource, String client, String userId, String resourceId);
    Completable delete(String client, String userId, String resourceId);
    Flowable<AccessPolicy> findAccessPolicies(String client, String user, String resource);
    Flowable<AccessPolicy> findAccessPoliciesByResources(List<String> resourceIds);
    Maybe<AccessPolicy> findAccessPolicy(String client, String user, String resource, String accessPolicy);
    Single<AccessPolicy> createAccessPolicy(AccessPolicy accessPolicy, String client, String user, String resource);
    Single<AccessPolicy> updateAccessPolicy(AccessPolicy accessPolicy, String client, String user, String resource, String accessPolicyId);
    Completable deleteAccessPolicy(String client, String user, String resource, String accessPolicy);
}
