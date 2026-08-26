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
package io.gravitee.am.repository.oauth2.api;

import io.gravitee.am.repository.common.ExpiredDataSweeper;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Storage for RFC 8628 device authorization requests.
 *
 * The stored expireAt is the device code expiry plus a retention window, so a request whose code
 * has lapsed is still returned by the finders until that window closes.
 *
 * @author GraviteeSource Team
 */
public interface DeviceAuthorizationRequestRepository extends ExpiredDataSweeper {

    /**
     * @param deviceCode the request identifier issued to the device
     */
    Maybe<DeviceAuthorizationRequest> findById(String deviceCode);

    /**
     * @param userCode the code entered by the end user on the verification page
     */
    Maybe<DeviceAuthorizationRequest> findByUserCode(String userCode);

    Single<DeviceAuthorizationRequest> create(DeviceAuthorizationRequest request);

    Single<DeviceAuthorizationRequest> update(DeviceAuthorizationRequest request);

    Single<DeviceAuthorizationRequest> updateStatus(String deviceCode, String status);

    Completable delete(String deviceCode);
}
