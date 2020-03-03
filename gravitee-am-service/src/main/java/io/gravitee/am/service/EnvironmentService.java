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

import java.util.List;

import io.gravitee.am.model.Environment;
import io.gravitee.am.service.model.NewEnvironment;
import io.gravitee.am.service.model.UpdateEnvironment;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * @author Florent CHAMFROY (florent.chamfroy at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface EnvironmentService {

    Maybe<Environment> findById(String environmentId);

    Single<List<Environment>> findAll();

    Single<List<Environment>> findByOrganization(String organizationId);

    Single<Environment> create(NewEnvironment environment);
    
    Single<Environment> update(UpdateEnvironment environment);

    Completable delete(String environmentId);
    
    Completable initialize();
}
