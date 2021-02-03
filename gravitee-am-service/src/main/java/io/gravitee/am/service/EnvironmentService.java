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
import io.gravitee.am.model.Environment;
import io.gravitee.am.service.model.NewEnvironment;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface EnvironmentService {

    Single<Environment> findById(String id, String organizationId);

    Flowable<Environment> findAll(String organizationId);

    Maybe<Environment> createDefault();

    Single<Environment> createOrUpdate(String organizationId, String environmentId, NewEnvironment newEnvironment, User createdBy);
}
 