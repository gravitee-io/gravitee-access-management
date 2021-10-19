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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.Device;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DeviceRepository extends CrudRepository<Device, String> {

    Maybe<Device> findByReferenceAndClientAndUserAndDeviceIdentifierAndDeviceId(
            ReferenceType referenceType, String referenceId, String client, String user, String rememberDevice, String deviceId);

    default Maybe<Device> findByDomainAndClientAndUserAndDeviceIdentifierAndDeviceId(String domain, String client, String user, String deviceIdentifier, String deviceId){
        return findByReferenceAndClientAndUserAndDeviceIdentifierAndDeviceId(ReferenceType.DOMAIN, domain, client, user, deviceIdentifier, deviceId);
    }

    Flowable<Device> findByReferenceAndUser(ReferenceType referenceType, String referenceId, String user);

    default Flowable<Device> findByDomainAndClientAndUser(String domain, String user) {
        return findByReferenceAndUser(ReferenceType.DOMAIN, domain, user);
    }

    default Completable purgeExpiredData() {
        return Completable.complete();
    }


}
