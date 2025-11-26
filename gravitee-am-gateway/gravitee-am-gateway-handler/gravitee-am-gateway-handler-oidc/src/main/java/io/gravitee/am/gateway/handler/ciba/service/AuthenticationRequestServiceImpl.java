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
package io.gravitee.am.gateway.handler.ciba.service;

import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierProvider;
import io.gravitee.am.authdevice.notifier.api.model.ADCallbackContext;
import io.gravitee.am.authdevice.notifier.api.model.ADNotificationRequest;
import io.gravitee.am.authdevice.notifier.api.model.ADNotificationResponse;
import io.gravitee.am.authdevice.notifier.api.model.ADUserResponse;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.ciba.exception.AuthenticationRequestExpiredException;
import io.gravitee.am.gateway.handler.ciba.exception.AuthenticationRequestNotFoundException;
import io.gravitee.am.gateway.handler.ciba.exception.AuthorizationPendingException;
import io.gravitee.am.gateway.handler.ciba.exception.SlowDownException;
import io.gravitee.am.gateway.handler.ciba.service.request.AuthenticationRequestStatus;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequest;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.manager.authdevice.notifier.AuthenticationDeviceNotifierManager;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oidc.api.CibaAuthRequestRepository;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static io.gravitee.am.common.oidc.Parameters.ACR_VALUES;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.STATE;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationRequestServiceImpl implements AuthenticationRequestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationRequestServiceImpl.class);

    @Autowired
    private CibaAuthRequestRepository authRequestRepository;

    @Autowired
    private Environment environment;

    @Autowired
    private Domain domain;

    @Autowired
    private AuthenticationDeviceNotifierManager notifierManager;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private ClientSyncService clientService;

    /**
     * How many time (in sec) an auth-request is kept into the DB
     * once it expired. (This retention is useful to manage the
     * expired_token error)
     */
    @Value("${openid.ciba.auth-request.retention:900}")
    private int requestRetentionInSec = 900;

    @Override
    public Single<CibaAuthRequest> register(CibaAuthenticationRequest request, Client client) {
        Instant now = Instant.now();
        final Integer requestedExpiry = request.getRequestedExpiry();
        final long ttl = requestedExpiry != null ? requestedExpiry: domain.getOidc().getCibaSettings().getAuthReqExpiry();

        CibaAuthRequest entity = new CibaAuthRequest();
        entity.setClientId(client.getClientId());
        entity.setId(request.getId());
        entity.setScopes(request.getScopes());
        entity.setSubject(request.getSubject());
        entity.setStatus(AuthenticationRequestStatus.ONGOING.name());
        entity.setCreatedAt(new Date(now.toEpochMilli()));
        entity.setLastAccessAt(new Date(now.toEpochMilli()));
        // as the application has to be informed of an expired request, we add retention time to the ttl
        // to avoid removing the request information from the database when ttl has expired
        entity.setExpireAt(new Date(now.plusSeconds(ttl + requestRetentionInSec).toEpochMilli()));
        // Store acrValues in externalInformation for later retrieval when generating ID token
        if (request.getAcrValues() != null && !request.getAcrValues().isEmpty()) {
            if (entity.getExternalInformation() == null) {
                entity.setExternalInformation(new java.util.HashMap<>());
            }
            entity.getExternalInformation().put(ACR_VALUES, request.getAcrValues());
        }

        LOGGER.debug("Register AuthenticationRequest with auth_req_id '{}' and expiry of '{}' seconds", entity.getId(), ttl);

        return authRequestRepository.create(entity);
    }

    @Override
    public Single<CibaAuthRequest> retrieve(Domain domain, String authReqId) {
        LOGGER.debug("Search for authentication request with id '{}'", authReqId);
        return this.authRequestRepository.findById(authReqId)
                .switchIfEmpty(Single.error(() -> new InvalidGrantException(authReqId)))
                .flatMap(request -> {
                    if ((request.getExpireAt().getTime() - (requestRetentionInSec * 1000)) < Instant.now().toEpochMilli()) {
                        return Single.error(new AuthenticationRequestExpiredException());
                    }
                    switch (AuthenticationRequestStatus.valueOf(request.getStatus())) {
                        case ONGOING:
                            // Check if the request interval is respected by the client
                            // if the client request to often the endpoint, throws a SlowDown error
                            // otherwise, update the last Access date before sending the pending exception
                            final int interval = domain.getOidc().getCibaSettings().getTokenReqInterval();
                            if (request.getLastAccessAt().toInstant().plusSeconds(interval).isAfter(Instant.now())) {
                                return Single.error(new SlowDownException());
                            }
                            request.setLastAccessAt(new Date());
                            return this.authRequestRepository.update(request).flatMap(__ -> Single.error(new AuthorizationPendingException()));
                        case REJECTED:
                            return this.authRequestRepository.delete(authReqId).toSingle(() -> { throw new AccessDeniedException(); });
                        default:
                            return this.authRequestRepository.delete(authReqId).toSingle(() -> request);
                    }
                });
    }

    @Override
    public Single<CibaAuthRequest> updateAuthDeviceInformation(CibaAuthRequest request) {
        LOGGER.debug("Update authentication request '{}' with AuthenticationDeviceNotifier information", request.getId());
        return this.authRequestRepository.findById(request.getId())
                .switchIfEmpty(Single.error(() -> new AuthenticationRequestNotFoundException(request.getId())))
                .flatMap(existingReq -> {
                    // update only information provided by the AD notifier
                    existingReq.setExternalTrxId(request.getExternalTrxId());
                    existingReq.setExternalInformation(request.getExternalInformation());
                    existingReq.setDeviceNotifierId(request.getDeviceNotifierId());
            return this.authRequestRepository.update(existingReq);
        });
    }

    @Override
    public Single<ADNotificationResponse> notify(ADNotificationRequest adRequest) {
        final AuthenticationDeviceNotifierProvider notifier = this.notifierManager.getAuthDeviceNotifierProvider(adRequest.getDeviceNotifierId());

        if (notifier == null) {
            return Single.error(new InvalidRequestException("No authentication device notifier defined"));
        }

        return notifier.notify(adRequest);
    }

    @Override
    public Completable validateUserResponse(ADCallbackContext context) {
        return Flowable.fromIterable(this.notifierManager.getAuthDeviceNotifierProviders())
                .flatMapSingle(provider -> provider.extractUserResponse(context))
                .filter(Optional::isPresent)
                .firstElement()
                .switchIfEmpty(Maybe.error(InvalidRequestException::new))
                .map(Optional::get)
                .flatMapSingle(userResponse -> {
                    final String status = userResponse.isValidated() ? AuthenticationRequestStatus.SUCCESS.name() : AuthenticationRequestStatus.REJECTED.name();
                    return this.jwtService.decode(userResponse.getState(), STATE)
                            .flatMap(jwtState -> verifyState(userResponse, jwtState.getAud())
                            ).flatMap(verifiedJwtState -> updateRequestStatus(verifiedJwtState.getJti(), status));
                }).ignoreElement();
    }

    private Single<JWT> verifyState(ADUserResponse userResponse, String clientId) {
        LOGGER.debug("Prepare verification of state '{}' with client id '{}'", userResponse.getState(), clientId);
        return this.clientService.findByClientId(clientId)
                .switchIfEmpty(Single.error(InvalidClientException::new))
                .flatMap(client -> Single.defer(() -> this.jwtService.decodeAndVerify(userResponse.getState(), client, STATE)))
                .filter(verifiedJwt -> userResponse.getTid().equals(verifiedJwt.getJti()))
                .switchIfEmpty(Single.error(() -> new InvalidRequestException("state parameter mismatch with the transaction id")))
                .onErrorResumeNext((error) -> {
                    LOGGER.debug("Verification of state '{}' fails on CIBA callback with client id '{}'", userResponse.getState(), clientId, error);
                    return Single.error(new InvalidRequestException("Invalid CIBA State"));
                });
    }

    private Single<CibaAuthRequest> updateRequestStatus(String reqExtId, String status) {
        return this.authRequestRepository.findByExternalId(reqExtId)
                .switchIfEmpty(Single.error(() -> new InvalidRequestException("Invalid CIBA State")))
                .flatMap(cibaRequest -> this.authRequestRepository.updateStatus(cibaRequest.getId(), status));
    }
}
