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
import io.gravitee.am.model.DeviceIdentifier;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.model.NewDeviceIdentifier;
import io.gravitee.am.service.model.UpdateDeviceIdentifier;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DeviceIdentifierService {

    Maybe<DeviceIdentifier> findById(String id);

    Flowable<DeviceIdentifier> findByDomain(String domain);

    Single<DeviceIdentifier> create(Domain domain, NewDeviceIdentifier newDeviceIdentifier, User principal);

    Single<DeviceIdentifier> update(Domain domain, String id, UpdateDeviceIdentifier updateDeviceIdentifier, User principal);

    Completable delete(String domain, String deviceIdentifierId, User principal);

    default Single<DeviceIdentifier> create(Domain domain, NewDeviceIdentifier newDeviceIdentifier) {
        return create(domain, newDeviceIdentifier, null);
    }

    default Single<DeviceIdentifier> update(Domain domain, String id, UpdateDeviceIdentifier updateDeviceIdentifier) {
        return update(domain, id, updateDeviceIdentifier, null);
    }

    default Completable delete(String domain, String deviceIdentifierId) {
        return delete(domain, deviceIdentifierId, null);
    }

    Completable deleteByDomain(String domainId);
}
