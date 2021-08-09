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
package io.gravitee.am.gateway.handler.oauth2.service.par.impl;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestObjectException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestUriException;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oauth2.service.par.PushedAuthorizationRequestResponse;
import io.gravitee.am.gateway.handler.oauth2.service.par.PushedAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.repository.oauth2.api.PushedAuthorizationRequestRepository;
import io.gravitee.am.repository.oauth2.model.PushedAuthorizationRequest;
import io.reactivex.*;
import io.reactivex.functions.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PushedAuthorizationRequestServiceImpl implements PushedAuthorizationRequestService {
    private static Logger LOGGER = LoggerFactory.getLogger(PushedAuthorizationRequestServiceImpl.class);

    /**
     * Validity in millis for the request_uri
     */
    @Value("${authorization.request_uri.validity:60000}")
    private int requestUriValidity = 60000;

    @Autowired
    private Domain domain;

    @Autowired
    private PushedAuthorizationRequestRepository parRepository;

    @Autowired
    private JWSService jwsService;

    @Autowired
    private JWEService jweService;

    @Autowired
    private JWKService jwkService;

    @Override
    public Single<JWT> readFromURI(String requestUri, Client client, OpenIDProviderMetadata oidcMetadata) {
        if (requestUri.startsWith(PAR_URN_PREFIX)) {
            // Extract the identifier
            String identifier = requestUri.substring(PAR_URN_PREFIX.length());

            return parRepository.findById(identifier)
                    .switchIfEmpty(Single.error(new InvalidRequestUriException()))
                    .flatMap((Function<PushedAuthorizationRequest, Single<JWT>>) req -> {
                        if (req.getParameters() != null &&
                                req.getExpireAt() != null &&
                                req.getExpireAt().after(new Date())) {

                            final String request = req.getParameters().getFirst(io.gravitee.am.common.oidc.Parameters.REQUEST);
                            if (request != null) {
                                return readRequestObject(client, request);
                            } else if (this.domain.usePlainFapiProfile()) {
                                return Single.error(new InvalidRequestException("request parameter is missing"));
                            } else {
                                // request object isn't specified, create a PlainJWT based on the parameters
                                final JWTClaimsSet.Builder builder = new JWTClaimsSet
                                        .Builder()
                                        .audience(oidcMetadata.getIssuer())
                                        .expirationTime(req.getExpireAt());
                                req.getParameters().toSingleValueMap().forEach((key, value) -> {
                                    builder.claim(key, value);
                                });
                                return Single.just(new PlainJWT(builder.build()));
                            }
                        }
                        return Single.error(new InvalidRequestUriException());
                    });
        } else {
            return Single.error(new InvalidRequestException("Invalid request_uri"));
        }
    }

    @Override
    public Single<PushedAuthorizationRequestResponse> registerParameters(PushedAuthorizationRequest par, Client client) {
        par.setClient(client.getId()); // link parameters to the internal client identifier
        par.setExpireAt(new Date(Instant.now().plusMillis(requestUriValidity).toEpochMilli()));

        Completable registrationValidation = Completable.fromAction(() -> {
            if (!client.getClientId().equals(par.getParameters().getFirst(Parameters.CLIENT_ID))) {
               throw new InvalidRequestException();
            }
            if (par.getParameters().getFirst(io.gravitee.am.common.oidc.Parameters.REQUEST_URI) != null) {
                throw new InvalidRequestException("request_uri not authorized");
            }
        });

        final String request = par.getParameters().getFirst(io.gravitee.am.common.oidc.Parameters.REQUEST);
        if (request != null) {
            registrationValidation = registrationValidation.andThen(Single.defer(() ->
                            readRequestObject(client, request)))
                    .ignoreElement();
        }

        return registrationValidation.andThen(Single.defer(() -> parRepository.create(par))).map(parPersisted -> {
            final PushedAuthorizationRequestResponse response = new PushedAuthorizationRequestResponse();
            response.setRequestUri(PAR_URN_PREFIX + parPersisted.getId());
            // the lifetime of the request URI in seconds as a positive integer
            final long exp = (parPersisted.getExpireAt().getTime() - Instant.now().toEpochMilli()) / 1000;
            response.setExp(exp);
            return response;
        });
    }

    private Single<JWT> readRequestObject(Client client, String request) {
        return jweService.decrypt(request, client)
                .onErrorResumeNext((ex) -> {
                    if (ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }
                    LOGGER.debug("JWT invalid for the request parameter", ex);
                    return Single.error(new InvalidRequestObjectException());
                })
                .map(jwt -> checkRequestObjectClaims(jwt))
                .map(this::checkRequestObjectAlgorithm)
                .flatMap(jwt -> validateSignature((SignedJWT) jwt, client));
    }

    private JWT checkRequestObjectClaims(JWT jwt) {
        try {

            if (jwt.getJWTClaimsSet().getStringClaim(io.gravitee.am.common.oidc.Parameters.REQUEST) != null
                    || jwt.getJWTClaimsSet().getStringClaim(io.gravitee.am.common.oidc.Parameters.REQUEST_URI) != null) {
                throw new InvalidRequestObjectException("Claims request and request_uri are forbidden");
            }

            return jwt;
        } catch (ParseException e) {
            LOGGER.warn("request object received in PAR request is malformed: {}", e.getMessage());
            throw new InvalidRequestObjectException();
        }
    }

    private JWT checkRequestObjectAlgorithm(JWT jwt) {
        // The authorization server shall verify that the request object is valid, the signature algorithm is not
        // none, and the signature is correct as in clause 6.3 of [OIDC].
        if (! (jwt instanceof SignedJWT) ||
                (jwt.getHeader().getAlgorithm() != null && "none".equalsIgnoreCase(jwt.getHeader().getAlgorithm().getName()))) {
           throw new InvalidRequestObjectException("Request object must be signed");
        }
        return jwt;
    }

    private Single<JWT> validateSignature(SignedJWT jwt, Client client) {
        return jwkService.getKeys(client)
                .switchIfEmpty(Maybe.error(new InvalidRequestObjectException()))
                .flatMap(new Function<JWKSet, MaybeSource<JWK>>() {
                    @Override
                    public MaybeSource<JWK> apply(JWKSet jwkSet) throws Exception {
                        return jwkService.getKey(jwkSet, jwt.getHeader().getKeyID());
                    }
                })
                .switchIfEmpty(Maybe.error(new InvalidRequestObjectException()))
                .flatMapSingle(new Function<JWK, SingleSource<JWT>>() {
                    @Override
                    public SingleSource<JWT> apply(JWK jwk) throws Exception {
                        // 6.3.2.  Signed Request Object
                        // To perform Signature Validation, the alg Header Parameter in the
                        // JOSE Header MUST match the value of the request_object_signing_alg
                        // set during Client Registration
                        if (jwt.getHeader().getAlgorithm().getName().equals(client.getRequestObjectSigningAlg()) &&
                                jwsService.isValidSignature(jwt, jwk)) {
                            return Single.just(jwt);
                        } else {
                            return Single.error(new InvalidRequestObjectException("Invalid signature"));
                        }
                    }
                });
    }

    @Override
    public Completable deleteRequestUri(String uriIdentifier) {
        LOGGER.debug("Delete Pushed Authorization Request with id '{}'", uriIdentifier);
        if (StringUtils.isEmpty(uriIdentifier)) {
            // if the identifier is null or empty, return successful operation.
            return Completable.complete();
        }
        return parRepository.delete(uriIdentifier);
    }
}