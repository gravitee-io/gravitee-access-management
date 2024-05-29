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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.flow.Flow;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface FlowService {

    Flowable<Flow> findAll(ReferenceType referenceType, String referenceId, boolean excludeApps);

    Flowable<Flow> findByApplication(ReferenceType referenceType, String referenceId, String application);

    List<Flow> defaultFlows(ReferenceType referenceType, String referenceId);

    Maybe<Flow> findById(ReferenceType referenceType, String referenceId, String id);

    Maybe<Flow> findById(String id);

    Single<Flow> create(ReferenceType referenceType, String referenceId, Flow flow, User principal);

    Single<Flow> create(ReferenceType referenceType, String referenceId, String application, Flow flow, User principal);

    Single<Flow> update(ReferenceType referenceType, String referenceId, String id, Flow flow, User principal);

    Single<List<Flow>> createOrUpdate(ReferenceType referenceType, String referenceId, List<Flow> flows, User principal);

    Single<List<Flow>> createOrUpdate(ReferenceType referenceType, String referenceId, String application, List<Flow> flows, User principal);

    Completable delete(String id, User principal);

    Single<String> getSchema();

    default Flowable<Flow> findAll(ReferenceType referenceType, String referenceId) {
        return findAll(referenceType, referenceId, false);
    }

    default Single<Flow> create(ReferenceType referenceType, String referenceId, Flow flow) {
        return create(referenceType, referenceId, flow, null);
    }

    default Single<Flow> create(ReferenceType referenceType, String referenceId, String application, Flow flow) {
        return create(referenceType, referenceId, application, flow, null);
    }

    default Single<Flow> update(ReferenceType referenceType, String referenceId, String id, Flow flow) {
        return update(referenceType, referenceId, id, flow, null);
    }

    default Single<List<Flow>> createOrUpdate(ReferenceType referenceType, String referenceId, List<Flow> flows) {
        return createOrUpdate(referenceType, referenceId, flows, null);
    }

    default Single<List<Flow>> createOrUpdate(ReferenceType referenceType, String referenceId, String application, List<Flow> flows) {
        return createOrUpdate(referenceType, referenceId, application, flows, null);
    }

    default Completable delete(String id) {
        return delete(id, null);
    }

    Single<List<Flow>> copyFromClient(String domain, String clientSource, String clientTarget);

}
