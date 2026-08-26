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

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.polling.PollingRequestState;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.gateway.handler.oauth2.exception.ExpiredTokenException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.polling.AbstractPollingRequestService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationDeviceFlowSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.api.DeviceAuthorizationRequestRepository;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;
import java.util.Set;

/**
 * @author GraviteeSource Team
 */
@CustomLog
public class DeviceAuthorizationRequestServiceImpl extends AbstractPollingRequestService<DeviceAuthorizationRequest>
        implements DeviceAuthorizationRequestService {

    static final int SLOW_DOWN_INCREMENT_IN_SEC = 5;

    static final int MAX_INTERVAL_IN_SEC = 60;

    @Autowired
    private DeviceAuthorizationRequestRepository requestRepository;

    @Autowired
    private Domain domain;

    /**
     * How long (in sec) a device authorization request is kept once it expired, so a device
     * polling late is told the code expired rather than that it does not exist.
     */
    @Value("${openid.deviceFlow.request.retention:900}")
    private int requestRetentionInSec = 900;

    @Override
    protected Maybe<DeviceAuthorizationRequest> findRequestById(String requestId) {
        return requestRepository.findById(requestId);
    }

    @Override
    protected Single<DeviceAuthorizationRequest> createRequest(DeviceAuthorizationRequest request) {
        return requestRepository.create(request);
    }

    @Override
    protected Single<DeviceAuthorizationRequest> updateRequest(DeviceAuthorizationRequest request) {
        return requestRepository.update(request);
    }

    @Override
    protected Completable deleteRequest(String requestId) {
        return requestRepository.delete(requestId);
    }

    @Override
    protected String initialStatus() {
        return DeviceAuthorizationRequestStatus.PENDING.name();
    }

    @Override
    protected PollingRequestState stateOf(DeviceAuthorizationRequest request) {
        return switch (DeviceAuthorizationRequestStatus.valueOf(request.getStatus())) {
            case PENDING -> PollingRequestState.PENDING;
            case DENIED -> PollingRequestState.DENIED;
            case APPROVED -> PollingRequestState.APPROVED;
        };
    }

    @Override
    protected int getRetentionInSec() {
        return requestRetentionInSec;
    }

    @Override
    protected String getRequestIdParameterName() {
        return Parameters.DEVICE_CODE;
    }

    @Override
    protected int computeIntervalInSec(DeviceAuthorizationRequest request, int configuredIntervalInSec) {
        return Math.min(configuredIntervalInSec + request.getIntervalIncrement(), MAX_INTERVAL_IN_SEC);
    }

    @Override
    protected Completable onSlowDown(DeviceAuthorizationRequest request, int configuredIntervalInSec) {
        if (computeIntervalInSec(request, configuredIntervalInSec) < MAX_INTERVAL_IN_SEC) {
            request.setIntervalIncrement(request.getIntervalIncrement() + SLOW_DOWN_INCREMENT_IN_SEC);
        }
        request.setLastAccessAt(new Date());
        return updateRequest(request).ignoreElement();
    }

    @Override
    public Single<DeviceAuthorizationRequest> register(Client client, Set<String> scopes) {
        final DeviceAuthorizationRequest request = new DeviceAuthorizationRequest();
        request.setId(RandomString.generate());
        request.setUserCode(UserCodeGenerator.generate());
        request.setScopes(scopes);
        request.setIntervalIncrement(0);
        log.debug("Register device authorization request '{}' for client {}", request.getId(), client.getClientId());
        return super.register(request, client.getClientId(), getDeviceCodeExpiryInSec(client));
    }

    @Override
    public Single<DeviceAuthorizationRequest> retrieve(String deviceCode, Client client) {
        log.debug("Search for device authorization request '{}' for client {}", deviceCode, client.getClientId());
        return super.retrieve(deviceCode, client, getPollingIntervalInSec(client));
    }

    @Override
    public Maybe<DeviceAuthorizationRequest> findByUserCode(String userCode) {
        final String normalized = UserCodeGenerator.normalize(userCode);
        if (normalized == null || normalized.isEmpty()) {
            return Maybe.empty();
        }
        return requestRepository.findByUserCode(normalized);
    }

    @Override
    public Single<DeviceAuthorizationRequest> approve(String deviceCode, String clientId, String subject, Set<String> scopes) {
        return stillActionable(deviceCode, clientId)
                .flatMap(request -> {
                    request.setStatus(DeviceAuthorizationRequestStatus.APPROVED.name());
                    request.setSubject(subject);
                    request.setScopes(scopes);
                    return requestRepository.update(request);
                });
    }

    @Override
    public Single<DeviceAuthorizationRequest> deny(String deviceCode, String clientId) {
        log.debug("Deny device authorization request '{}' for client {}", deviceCode, clientId);
        return stillActionable(deviceCode, clientId)
                .flatMap(request -> {
                    request.setStatus(DeviceAuthorizationRequestStatus.DENIED.name());
                    return requestRepository.update(request);
                });
    }

    private Single<DeviceAuthorizationRequest> stillActionable(String deviceCode, String clientId) {
        return requestRepository.findById(deviceCode)
                .switchIfEmpty(Single.error(() -> new InvalidGrantException(deviceCode)))
                .flatMap(request -> {
                    if (!request.getClientId().equals(clientId)) {
                        return Single.error(new InvalidGrantException(deviceCode));
                    }
                    if (isExpired(request)) {
                        return Single.error(new ExpiredTokenException());
                    }
                    if (!isPending(request)) {
                        return Single.error(new InvalidGrantException(deviceCode));
                    }
                    return Single.just(request);
                });
    }

    @Override
    public boolean isPending(DeviceAuthorizationRequest request) {
        return DeviceAuthorizationRequestStatus.PENDING.name().equals(request.getStatus()) && !isExpired(request);
    }

    @Override
    public boolean isExpired(DeviceAuthorizationRequest request) {
        return hasExpired(request);
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
