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
package io.gravitee.am.gateway.handler.ciba.resources.handler;

import io.gravitee.am.authdevice.notifier.api.model.ADNotificationRequest;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.CIBADeliveryMode;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.gateway.handler.ciba.service.AuthenticationRequestService;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequest;
import io.gravitee.am.gateway.handler.ciba.service.response.CibaAuthenticationResponse;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.CIBASettingNotifier;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.CIBA_AUTH_REQUEST_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationRequestAcknowledgeHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationRequestAcknowledgeHandler.class);

    private AuthenticationRequestService authRequestService;

    private Domain domain;

    private JWTService jwtService;

    public AuthenticationRequestAcknowledgeHandler(AuthenticationRequestService authRequestService, Domain domain, JWTService jwtService) {
        this.authRequestService = authRequestService;
        this.domain = domain;
        this.jwtService = jwtService;
    }

    @Override
    public void handle(RoutingContext context) {
        final CibaAuthenticationRequest authRequest = context.get(CIBA_AUTH_REQUEST_KEY);
        if (authRequest != null) {
            final Client client = context.get(CLIENT_CONTEXT_KEY);

            final List<CIBASettingNotifier> deviceNotifiers = this.domain.getOidc().getCibaSettings().getDeviceNotifiers();
            if (CollectionUtils.isEmpty(deviceNotifiers)) {
                LOGGER.warn("CIBA Authentication Request can't be processed without device notifier configured");
                context.fail(new InvalidRequestException("No Device notifier configure for the domain"));
                return;
            }

            // as a first implementation, we only manage a single notifier
            // in future release we may manage multiple one and select the right one
            // base one context information.
            final String authDeviceNotifierId = deviceNotifiers.get(0).getId();

            if (authRequest.getId() == null) {
                final String authReqId = SecureRandomString.generate();
                authRequest.setId(authReqId);
            }

            LOGGER.debug("CIBA Authentication Request linked to auth_req_id '{}'", authRequest);

            final int expiresIn = authRequest.getRequestedExpiry() != null ? authRequest.getRequestedExpiry() : domain.getOidc().getCibaSettings().getAuthReqExpiry();
            final String externalTrxId = SecureRandomString.generate();

            // Forge a state token to validate the callback response
            JWT jwt = new JWT();
            jwt.setIss(client.getDomain());
            final Instant now = Instant.now();
            jwt.setIat(now.getEpochSecond());
            jwt.setExp(now.plusSeconds(expiresIn).getEpochSecond());
            jwt.setAud(client.getClientId());
            jwt.setSub(authRequest.getSubject());
            jwt.setJti(externalTrxId);
            this.jwtService.encode(jwt, client)
                    .flatMap(stateJwt ->
                            this.authRequestService.register(authRequest, client)
                                    .flatMap(req -> {

                                        final ADNotificationRequest adRequest = new ADNotificationRequest();
                                        adRequest.setExpiresIn(expiresIn);
                                        adRequest.setAcrValues(authRequest.getAcrValues());
                                        adRequest.setMessage(authRequest.getBindingMessage());
                                        adRequest.setScopes(authRequest.getScopes());
                                        adRequest.setSubject(authRequest.getSubject());
                                        adRequest.setState(stateJwt);
                                        adRequest.setTransactionId(externalTrxId);
                                        adRequest.setDeviceNotifierId(authDeviceNotifierId);
                                        adRequest.setContext(context);

                                        return authRequestService.notify(adRequest)
                                                .flatMap(adResponse -> {
                                                    // Preserve existing externalInformation (including acrValues) and merge with new extraData
                                                    Map<String, Object> existingExternalInfo = req.getExternalInformation();
                                                    Map<String, Object> newExtraData = adResponse.getExtraData() != null ? adResponse.getExtraData() : new HashMap<>();

                                                    if (existingExternalInfo == null) {
                                                        existingExternalInfo = new java.util.HashMap<>();
                                                    }

                                                    // Create a new map with existing data, then add/update with new extraData
                                                    Map<String, Object> mergedExternalInfo = new HashMap<>(existingExternalInfo);
                                                    mergedExternalInfo.putAll(newExtraData);

                                                    req.setExternalInformation(mergedExternalInfo);
                                                    req.setExternalTrxId(adResponse.getTransactionId());
                                                    return authRequestService.updateAuthDeviceInformation(req);
                                                });
                                    })
                    ).subscribe(req -> {

                        CibaAuthenticationResponse response = new CibaAuthenticationResponse();
                        response.setAuthReqId(req.getId());
                        response.setExpiresIn(req.getExpireAt().toInstant().minusMillis(req.getCreatedAt().getTime()).getEpochSecond());

                        // specify rate limit for Poll and Ping mode
                        if (client.getBackchannelTokenDeliveryMode()!= null && !client.getBackchannelTokenDeliveryMode().equals(CIBADeliveryMode.PUSH)) {
                            response.setInterval(domain.getOidc().getCibaSettings().getTokenReqInterval());
                        }

                        context
                                .response()
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .setStatusCode(HttpStatusCode.OK_200)
                                .end(Json.encodePrettily(response));

                    }, error -> {
                        LOGGER.error("Unable to persist CIBA AuthenticationRequest object", error);
                        context.fail(error);
                    });

            return;
        } else {
            LOGGER.error("CIBA Authentication Request object is null");
            context.fail(new InvalidRequestException("Missing authentication request"));
        }
    }
}
