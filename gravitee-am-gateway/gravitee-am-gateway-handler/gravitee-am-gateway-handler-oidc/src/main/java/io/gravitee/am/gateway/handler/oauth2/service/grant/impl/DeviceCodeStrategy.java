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
package io.gravitee.am.gateway.handler.oauth2.service.grant.impl;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Strategy for the Device Authorization Grant (RFC 8628): the device polls the token endpoint with
 * the device code it was issued, and receives tokens once its user has approved.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8628#section-3.4">RFC 8628 Section 3.4</a>
 * @author GraviteeSource Team
 */
@CustomLog
public class DeviceCodeStrategy implements GrantStrategy {

    private final DeviceAuthorizationRequestService deviceAuthorizationRequestService;
    private final UserAuthenticationManager userAuthenticationManager;

    public DeviceCodeStrategy(DeviceAuthorizationRequestService deviceAuthorizationRequestService,
                              UserAuthenticationManager userAuthenticationManager) {
        this.deviceAuthorizationRequestService = deviceAuthorizationRequestService;
        this.userAuthenticationManager = userAuthenticationManager;
    }

    @Override
    public boolean supports(String grantType, Client client, Domain domain) {
        if (!GrantType.DEVICE_CODE.equals(grantType)) {
            return false;
        }
        if (!domain.useDeviceFlow()) {
            log.debug("Device flow is disabled on domain {}", domain.getName());
            return false;
        }
        if (!client.hasGrantType(GrantType.DEVICE_CODE)) {
            log.debug("Client {} does not support the device_code grant type", client.getClientId());
            return false;
        }
        return true;
    }

    @Override
    public Single<TokenCreationRequest> process(TokenRequest request, Client client, Domain domain) {
        final String deviceCode = request.parameters().getFirst(Parameters.DEVICE_CODE);
        if (isBlank(deviceCode)) {
            return Single.error(new InvalidRequestException("Missing parameter: " + Parameters.DEVICE_CODE));
        }

        return deviceAuthorizationRequestService.retrieve(deviceCode, client)
                .flatMap(deviceRequest -> {
                    request.setScopes(deviceRequest.getScopes());
                    return userAuthenticationManager.loadPreAuthenticatedUser(deviceRequest.getSubject(), request)
                            .switchIfEmpty(Single.error(() -> new InvalidGrantException("User not found")))
                            .onErrorResumeNext(ex -> Single.error(
                                    new InvalidGrantException(isBlank(ex.getMessage()) ? "unable to read user profile" : ex.getMessage())))
                            .map(user -> TokenCreationRequest.forDeviceCode(
                                    request,
                                    user,
                                    deviceCode,
                                    client.hasGrantType(GrantType.REFRESH_TOKEN)));
                });
    }
}
