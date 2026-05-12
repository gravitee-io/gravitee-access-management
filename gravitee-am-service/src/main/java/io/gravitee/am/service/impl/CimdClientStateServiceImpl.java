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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.CimdClientState;
import io.gravitee.am.model.Domain;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.CimdClientStateService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author GraviteeSource Team
 */
@Component
public class CimdClientStateServiceImpl implements CimdClientStateService {

    @Autowired
    private DataPlaneRegistry dataPlaneRegistry;

    @Override
    public Maybe<CimdClientState> findByDomainAndClientId(Domain domain, String clientId) {
        return dataPlaneRegistry.getCimdClientStateRepository(domain).findByDomainAndClientId(domain.getId(), clientId);
    }

    @Override
    public Single<CimdClientState> upsert(Domain domain, String clientId, String monitoredPropertiesHash) {
        final CimdClientState state = new CimdClientState();
        state.setDomainId(domain.getId());
        state.setClientId(clientId);
        state.setMonitoredPropertiesHash(monitoredPropertiesHash);
        return dataPlaneRegistry.getCimdClientStateRepository(domain).upsert(state);
    }

    @Override
    public Completable deleteByDomain(Domain domain) {
        return dataPlaneRegistry.getCimdClientStateRepository(domain).deleteByDomain(domain.getId());
    }
}
