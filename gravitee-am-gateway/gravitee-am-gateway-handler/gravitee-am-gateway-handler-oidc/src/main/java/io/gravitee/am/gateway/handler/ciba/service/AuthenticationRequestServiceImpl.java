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
import io.gravitee.am.authdevice.notifier.api.IdentityProviderDependent;
import io.gravitee.am.authdevice.notifier.api.model.ADCallbackContext;
import io.gravitee.am.authdevice.notifier.api.model.ADNotificationRequest;
import io.gravitee.am.authdevice.notifier.api.model.ADNotificationResponse;
import io.gravitee.am.authdevice.notifier.api.model.ADStatusRequest;
import io.gravitee.am.authdevice.notifier.api.model.ADUserResponse;
import io.gravitee.am.authdevice.notifier.api.model.FederatedConnection;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.ciba.exception.AuthenticationRequestExpiredException;
import io.gravitee.am.gateway.handler.ciba.exception.AuthenticationRequestNotFoundException;
import io.gravitee.am.gateway.handler.ciba.exception.AuthorizationPendingException;
import io.gravitee.am.gateway.handler.ciba.exception.AuthorizationRejectedException;
import io.gravitee.am.gateway.handler.ciba.exception.SlowDownException;
import io.gravitee.am.gateway.handler.ciba.service.request.AuthenticationRequestStatus;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequest;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.manager.authdevice.notifier.AuthenticationDeviceNotifierManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectAuthenticationProvider;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectIdentityProviderConfiguration;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oidc.api.CibaAuthRequestRepository;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static io.gravitee.am.common.oidc.Parameters.ACR_VALUES;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.STATE;
import lombok.CustomLog;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class AuthenticationRequestServiceImpl implements AuthenticationRequestService {


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
    @Qualifier("regularClientLookupService")
    private ClientLookupService clientLookupService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private UserAuthenticationManager userAuthenticationManager;

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
        // Copy RFC 9396 authorization_details onto the persisted entity (presence-gated; no flag read)
        if (request.getAuthorizationDetails() != null && !request.getAuthorizationDetails().isEmpty()) {
            entity.setAuthorizationDetails(request.getAuthorizationDetails());
        }
        // Store acrValues in externalInformation for later retrieval when generating ID token
        if (request.getAcrValues() != null && !request.getAcrValues().isEmpty()) {
            if (entity.getExternalInformation() == null) {
                entity.setExternalInformation(new java.util.HashMap<>());
            }
            entity.getExternalInformation().put(ACR_VALUES, request.getAcrValues());
        }

        log.debug("Register AuthenticationRequest with auth_req_id '{}' and expiry of '{}' seconds for client {}", entity.getId(), ttl, client.getClientId());

        return authRequestRepository.create(entity);
    }

    @Override
    public Single<CibaAuthRequest> retrieve(Domain domain, String authReqId, Client client, Request request) {
        log.debug("Search for authentication request with id {} for client {}", authReqId, client.getClientId());
        return this.authRequestRepository.findById(authReqId)
                .switchIfEmpty(Single.error(() -> new InvalidGrantException(authReqId)))
                .flatMap(authRequest -> {
                    if ((authRequest.getExpireAt().getTime() - (requestRetentionInSec * 1000)) < Instant.now().toEpochMilli()) {
                        return Single.error(new AuthenticationRequestExpiredException());
                    }
                    if (!client.getClientId().equals(authRequest.getClientId())) {
                        return Single.error(new InvalidGrantException(String.format("Invalid client: auth_req_id '%s' issued to client '%s' cannot be used by client '%s'", authReqId, authRequest.getClientId(), client.getClientId())));
                    }
                    if (AuthenticationRequestStatus.valueOf(authRequest.getStatus()) == AuthenticationRequestStatus.ONGOING) {
                        return poll(domain, authRequest, request);
                    }
                    return conclude(authRequest);
                });
    }

    private Single<CibaAuthRequest> poll(Domain domain, CibaAuthRequest authRequest, Request request) {
        // Check if the request interval is respected by the client
        // if the client request to often the endpoint, throws a SlowDown error
        // otherwise, update the last Access date before asking the notifier for a decision
        final int interval = domain.getOidc().getCibaSettings().getTokenReqInterval();
        if (authRequest.getLastAccessAt().toInstant().plusSeconds(interval).isAfter(Instant.now())) {
            return Single.error(new SlowDownException());
        }
        authRequest.setLastAccessAt(new Date());
        return this.authRequestRepository.update(authRequest)
                .flatMap(saved -> checkStatus(saved)
                        .flatMap(decision -> decision.isPresent()
                                ? completeOrReject(saved, decision.get(), request).flatMap(this::conclude)
                                : Single.error(new AuthorizationPendingException())));
    }

    private Single<CibaAuthRequest> completeOrReject(CibaAuthRequest authRequest, ADUserResponse decision, Request request) {
        // Once the notifier has decided, the upstream grant is spent: a failed completion cannot be retried.
        return complete(authRequest, decision, request)
                .onErrorResumeNext(error -> {
                    log.warn("CIBA completion failed for auth_req_id {}; rejecting the request", authRequest.getId(), error);
                    return this.authRequestRepository.updateStatus(authRequest.getId(), AuthenticationRequestStatus.REJECTED.name())
                            .doOnError(persistError -> log.error("CIBA could not persist the rejection of auth_req_id {}",
                                    authRequest.getId(), persistError));
                });
    }

    private Single<Optional<ADUserResponse>> checkStatus(CibaAuthRequest authRequest) {
        final AuthenticationDeviceNotifierProvider notifier = authRequest.getDeviceNotifierId() == null ? null
                : this.notifierManager.getAuthDeviceNotifierProvider(authRequest.getDeviceNotifierId());
        if (notifier == null) {
            if (authRequest.getDeviceNotifierId() != null) {
                log.warn("CIBA device notifier {} of auth_req_id {} is not deployed; the request stays pending",
                        authRequest.getDeviceNotifierId(), authRequest.getId());
            }
            return Single.just(Optional.empty());
        }
        return federatedConnection(notifier)
                .map(connection -> new ADStatusRequest(authRequest.getExternalTrxId(), authRequest.getExternalInformation(), connection))
                .defaultIfEmpty(new ADStatusRequest(authRequest.getExternalTrxId(), authRequest.getExternalInformation(), null))
                .flatMap(notifier::checkStatus);
    }

    private Single<CibaAuthRequest> conclude(CibaAuthRequest authRequest) {
        if (AuthenticationRequestStatus.valueOf(authRequest.getStatus()) == AuthenticationRequestStatus.REJECTED) {
            return this.authRequestRepository.delete(authRequest.getId()).toSingle(() -> { throw new AuthorizationRejectedException(); });
        }
        return this.authRequestRepository.delete(authRequest.getId()).toSingle(() -> authRequest);
    }

    @Override
    public Single<CibaAuthRequest> updateAuthDeviceInformation(CibaAuthRequest request) {
        log.debug("Update authentication request '{}' with AuthenticationDeviceNotifier information", request.getId());
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

        return federatedConnection(notifier)
                .map(connection -> {
                    adRequest.setConnection(connection);
                    return adRequest;
                })
                .defaultIfEmpty(adRequest)
                .flatMap(notifier::notify);
    }

    private Maybe<FederatedConnection> federatedConnection(AuthenticationDeviceNotifierProvider notifier) {
        final String idpId = (notifier instanceof IdentityProviderDependent dep) ? dep.getIdentityProviderId().orElse(null) : null;
        if (idpId == null) {
            return Maybe.empty();
        }
        if (this.identityProviderManager.getIdentityProvider(idpId) == null) {
            log.warn("CIBA federation identity provider {} not found", idpId);
            return Maybe.error(new InvalidRequestException("CIBA federation identity provider is unavailable"));
        }
        return this.identityProviderManager.get(idpId)
                .switchIfEmpty(Maybe.defer(() -> {
                    log.warn("CIBA federation identity provider {} is not deployed", idpId);
                    return Maybe.error(new InvalidRequestException("CIBA federation identity provider is unavailable"));
                }))
                .map(AuthenticationRequestServiceImpl::toFederatedConnection);
    }

    static FederatedConnection toFederatedConnection(AuthenticationProvider provider) {
        if (!(provider instanceof OpenIDConnectAuthenticationProvider<?> oidc)) {
            throw new InvalidRequestException("CIBA federation IdP is not an OpenID Connect provider");
        }
        OpenIDConnectIdentityProviderConfiguration cfg = oidc.getConfiguration();
        String scope = (cfg.getScopes() != null) ? String.join(" ", cfg.getScopes()) : "";
        return new FederatedConnection(cfg.getClientId(), cfg.getClientSecret(), scope, cfg.getWellKnownUri(),
                cfg.getClientAuthenticationMethod());
    }

    @Override
    public Completable validateUserResponse(ADCallbackContext context, Request request) {
        return Flowable.fromIterable(this.notifierManager.getAuthDeviceNotifierProviders())
                .flatMapSingle(provider -> provider.extractUserResponse(context))
                .filter(Optional::isPresent).firstElement()
                .switchIfEmpty(Maybe.error(InvalidRequestException::new))
                .map(Optional::get)
                .flatMapSingle(userResponse -> this.jwtService.decode(userResponse.getState(), STATE)
                        .flatMap(jwtState -> this.clientLookupService.findByClientId(jwtState.getAud())
                                .switchIfEmpty(Single.error(InvalidClientException::new))
                                .flatMap(client -> verifyState(userResponse, client)
                                        .flatMap(verified -> updateRequestStatus(verified.getJti(), userResponse, request)))))
                .ignoreElement();
    }

    private Single<JWT> verifyState(ADUserResponse userResponse, Client client) {
        return Single.defer(() -> this.jwtService.decodeAndVerify(userResponse.getState(), client, STATE))
                .filter(verifiedJwt -> userResponse.getTid().equals(verifiedJwt.getJti()))
                .switchIfEmpty(Single.error(() -> new InvalidRequestException("state parameter mismatch with the transaction id")))
                .onErrorResumeNext(error -> {
                    if (error instanceof InvalidRequestException) {
                        return Single.error(error);
                    }
                    // Log the real cause (key rotation / clock skew / decode failure) but keep the
                    // generic client-facing message on this security-critical state check.
                    log.warn("CIBA state verification failed for tid={}: {}", userResponse.getTid(), error.getMessage(), error);
                    return Single.error(new InvalidRequestException("Invalid CIBA State"));
                });
    }

    private Single<CibaAuthRequest> updateRequestStatus(String reqExtId, ADUserResponse userResponse, Request request) {
        return this.authRequestRepository.findByExternalId(reqExtId)
                .switchIfEmpty(Single.error(() -> new InvalidRequestException("Invalid CIBA State")))
                .flatMap(cibaRequest -> complete(cibaRequest, userResponse, request));
    }

    private Single<CibaAuthRequest> complete(CibaAuthRequest cibaRequest, ADUserResponse userResponse, Request request) {
        final String status = userResponse.isValidated() ? AuthenticationRequestStatus.SUCCESS.name() : AuthenticationRequestStatus.REJECTED.name();
        final String idpId = userResponse.isValidated() ? userResponse.getIdentityProviderId() : null;
        if (idpId == null) {
            // non-federated, rejected, or response carries no IdP -> status-only
            return this.authRequestRepository.updateStatus(cibaRequest.getId(), status);
        }
        final AuthenticationContext authContext = new SimpleAuthenticationContext();
        return this.identityProviderManager.get(idpId)
                .switchIfEmpty(Single.error(() -> new InvalidRequestException("CIBA federation IdP not available: " + idpId)))
                .flatMap(provider -> provider.retrieveUserFromTokenResponse(
                                userResponse.getAccessToken(), userResponse.getIdToken(), authContext)
                        .switchIfEmpty(Single.error(() -> new InvalidRequestException("CIBA federation: empty user from IdP"))))
                .flatMap(principal -> {
                    if (!(principal instanceof io.gravitee.am.identityprovider.api.DefaultUser du)) {
                        return Single.error(new InvalidRequestException("CIBA federation: IdP returned non-DefaultUser principal"));
                    }
                    java.util.Map<String, Object> info = du.getAdditionalInformation() != null
                            ? new java.util.HashMap<>(du.getAdditionalInformation()) : new java.util.HashMap<>();
                    info.put("source", idpId);
                    du.setAdditionalInformation(info);
                    // Completion is an authentication event: connect() (UserAuthenticationServiceImpl#create0)
                    // derives lastIdentityUsed from source and records loggedAt/loginsCount when
                    // afterAuthentication=true — no need to plant last_identity by hand.
                    return this.userAuthenticationManager.connect(du, null, request, true);
                })
                .flatMap(localUser -> {
                    cibaRequest.setSubject(localUser.getId());
                    return this.authRequestRepository.update(cibaRequest)
                            .flatMap(saved -> this.authRequestRepository.updateStatus(saved.getId(), status));
                });
    }
}
