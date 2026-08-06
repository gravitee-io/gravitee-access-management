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
package io.gravitee.am.service.dataplane;

import io.gravitee.am.model.Domain;
import io.reactivex.rxjava3.core.Completable;

/**
 * A store in the data plane that holds data of its own for a domain. Implement this on the service
 * that owns the store, and the domain deletion picks it up: the management API collects every
 * implementation rather than naming them one by one, so a store added later is purged without
 * anyone remembering to extend the deletion.
 *
 * @author GraviteeSource Team
 */
public interface DomainDataPlaneCleanup {

    /**
     * Remove everything this store holds for the domain. Called once the domain itself is deleted,
     * on a best effort basis: the data plane may be unreachable, and the domain still has to go.
     */
    Completable purgeDataPlane(Domain domain);
}
