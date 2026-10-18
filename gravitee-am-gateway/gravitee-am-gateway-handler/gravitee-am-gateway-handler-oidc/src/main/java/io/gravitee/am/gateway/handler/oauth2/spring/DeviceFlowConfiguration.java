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
package io.gravitee.am.gateway.handler.oauth2.spring;

import io.gravitee.am.gateway.handler.api.ProtocolConfiguration;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.service.polling.PollingService;
import io.gravitee.am.gateway.handler.common.service.polling.impl.PollingServiceImpl;
import io.gravitee.am.gateway.handler.common.service.polling.impl.WidenIntervalPollingPolicy;
import io.gravitee.am.gateway.handler.oauth2.DeviceFlowProvider;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestStatus;
import io.gravitee.am.repository.oauth2.api.DeviceAuthorizationRequestRepository;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeviceFlowConfiguration implements ProtocolConfiguration {

    @Bean
    public ProtocolProvider deviceFlowProvider() {
        return new DeviceFlowProvider();
    }

    @Bean
    public DeviceAuthorizationRequestService deviceAuthorizationRequestService() {
        return new DeviceAuthorizationRequestServiceImpl();
    }

    @Bean
    public PollingService<DeviceAuthorizationRequest> deviceCodePollingService(DeviceAuthorizationRequestRepository deviceAuthorizationRequestRepository,
                                                                               @Value("${openid.deviceFlow.request.retention:900}") int retentionInSec) {
        return new PollingServiceImpl<>(deviceAuthorizationRequestRepository,
                request -> DeviceAuthorizationRequestStatus.valueOf(request.getStatus()).pollingState(),
                retentionInSec, new WidenIntervalPollingPolicy<>());
    }
}
