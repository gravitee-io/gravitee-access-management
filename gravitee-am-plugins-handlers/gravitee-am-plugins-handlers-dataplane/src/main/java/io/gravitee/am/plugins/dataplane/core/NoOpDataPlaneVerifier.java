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
package io.gravitee.am.plugins.dataplane.core;

import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.reactivex.rxjava3.core.Completable;

/**
 * Serves every data plane without asking its store anything. Wired by the gateway, which loads only
 * what the gravitee.yml declares, and by a management node that has verification turned off.
 *
 * @author GraviteeSource Team
 */
public class NoOpDataPlaneVerifier implements DataPlaneVerifier {

    @Override
    public void reserve(String dataPlaneId) {
        // nothing is claimed, so nothing is ever refused for want of a check
    }

    @Override
    public void require(String dataPlaneId, DataPlaneProvider provider) {
        // no data plane is put under verification
    }

    @Override
    public Completable verified(String dataPlaneId) {
        return Completable.complete();
    }

    @Override
    public void forget(String dataPlaneId) {
        // nothing is remembered about an id in the first place
    }
}
