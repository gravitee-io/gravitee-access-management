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

import io.gravitee.am.service.model.DataPlaneDefinitionSummary;
import io.gravitee.am.service.model.NewDataPlaneDefinition;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Every read returns a {@link DataPlaneDefinitionSummary} rather than the stored definition: the
 * {@code configuration} blob can hold connection credentials and must not leave this layer. The
 * data plane loader (AM-7260) reads the repository directly for the raw settings.
 *
 * @author GraviteeSource Team
 */
public interface DataPlaneDefinitionService {

    Single<DataPlaneDefinitionSummary> create(NewDataPlaneDefinition newDataPlaneDefinition);

    Flowable<DataPlaneDefinitionSummary> findAll();

    Single<DataPlaneDefinitionSummary> findById(String id);

    /**
     * Deletes a definition, refused while any domain still points at it.
     */
    Completable delete(String id);
}
