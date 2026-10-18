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
package io.gravitee.am.gateway.handler.oauth2.service.device;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.gateway.handler.common.service.polling.PollingService;
import io.gravitee.am.gateway.handler.common.service.polling.PollingWindow;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationDeviceFlowSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.api.DeviceAuthorizationRequestRepository;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Set;

/**
 * @author GraviteeSource Team
 */
@CustomLog
public class DeviceAuthorizationRequestServiceImpl implements DeviceAuthorizationRequestService {

    @Autowired
    private DeviceAuthorizationRequestRepository requestRepository;

    @Autowired
    @Qualifier("deviceCodePollingService")
    private PollingService<DeviceAuthorizationRequest> pollingService;

    @Autowired
    private Domain domain;

    @Override
    public Single<DeviceAuthorizationRequest> register(Client client, Set<String> scopes) {
        final PollingWindow window = pollingService.window(getDeviceCodeExpiryInSec(client));
        final DeviceAuthorizationRequest request = new DeviceAuthorizationRequest();
        request.setId(RandomString.generate());
        request.setClientId(client.getClientId());
        request.setUserCode(UserCodeGenerator.generate());
        request.setScopes(scopes);
        request.setStatus(DeviceAuthorizationRequestStatus.PENDING.name());
        request.setSlowDownCount(0);
        request.setCreatedAt(window.createdAt());
        request.setLastAccessAt(window.createdAt());
        request.setExpireAt(window.expireAt());
        log.debug("Register device authorization request '{}' for client {}", request.getId(), client.getClientId());
        return requestRepository.create(request);
    }

    @Override
    public int getDeviceCodeExpiryInSec(Client client) {
        return deviceFlowSettings(client).getDeviceCodeExpiry();
    }

    @Override
    public int getPollingIntervalInSec(Client client) {
        return deviceFlowSettings(client).getPollingInterval();
    }

    private ApplicationDeviceFlowSettings deviceFlowSettings(Client client) {
        return ApplicationDeviceFlowSettings.getInstance(domain, client);
    }
}
