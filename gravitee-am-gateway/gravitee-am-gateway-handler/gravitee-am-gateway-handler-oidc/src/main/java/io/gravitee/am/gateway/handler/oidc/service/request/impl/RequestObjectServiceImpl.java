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
package io.gravitee.am.gateway.handler.oidc.service.request.impl;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.oauth2.InvalidRequestObjectException;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectRegistrationRequest;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectRegistrationResponse;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.repository.oidc.api.RequestObjectRepository;
import io.gravitee.am.repository.oidc.model.RequestObject;
import io.gravitee.common.utils.UUID;
import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.functions.Function;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URISyntaxException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Teams
 */
public class RequestObjectServiceImpl implements RequestObjectService {

    @Autowired
    public WebClient webClient;

    @Autowired
    private JWSService jwsService;

    @Autowired
    private JWEService jweService;

    @Autowired
    private JWKService jwkService;

    @Autowired
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Autowired
    private RequestObjectRepository requestObjectRepository;

    @Override
    public Single<JWT> readRequestObject(String request, Client client) {
        return jweService.decrypt(request, client)
                .onErrorResumeNext(Single.error(new InvalidRequestObjectException("Malformed request object")))
                .flatMap((Function<JWT, SingleSource<JWT>>) jwt -> {
                    if (jwt instanceof SignedJWT) {
                        return validateSignature((SignedJWT) jwt, client);
                    } else {
                        return Single.just(jwt);
                    }
                });
    }

    @Override
    public Single<JWT> readRequestObjectFromURI(String requestUri, Client client) {
        try {
            if (requestUri.startsWith(RESOURCE_OBJECT_URN_PREFIX)) {
                // Extract the identifier
                String identifier = requestUri.substring(RESOURCE_OBJECT_URN_PREFIX.length());

                return requestObjectRepository.findById(identifier)
                        .switchIfEmpty(Single.error(new InvalidRequestObjectException()))
                        .flatMap((Function<RequestObject, Single<JWT>>) req -> {
                            if (req.getExpireAt().after(new Date())) {
                                return readRequestObject(req.getPayload(), client);
                            }

                            return Single.error(new InvalidRequestObjectException());
                        });
            } else {
                return webClient.getAbs(UriBuilder.fromHttpUrl(requestUri).build().toString())
                        .rxSend()
                        .map(HttpResponse::bodyAsString)
                        .flatMap((Function<String, Single<JWT>>) s -> readRequestObject(s, client));
            }
        }
        catch (IllegalArgumentException | URISyntaxException ex) {
            return Single.error(new InvalidRequestObjectException(requestUri+" is not valid."));
        }
    }

    @Override
    public Single<RequestObjectRegistrationResponse> registerRequestObject(RequestObjectRegistrationRequest request, Client client) {
        try {
            JWT jwt = JWTParser.parse(request.getRequest());

            // The authorization server shall verify that the request object is valid, the signature algorithm is not
            // none, and the signature is correct as in clause 6.3 of [OIDC].
            if (! (jwt instanceof SignedJWT) || jwt.getHeader().getAlgorithm() == Algorithm.NONE) {
                    return Single.error(new InvalidRequestObjectException("Request object must be signed"));
            }

            SignedJWT signedJWT = (SignedJWT) jwt;

            return validateSignature(signedJWT, client)
                    .flatMap(new Function<JWT, SingleSource<RequestObject>>() {
                        @Override
                        public SingleSource<RequestObject> apply(JWT jwt) throws Exception {
                            RequestObject requestObject = new RequestObject();
                            requestObject.setId(UUID.random().toString());
                            requestObject.setClient(client.getId());
                            requestObject.setDomain(client.getDomain());
                            requestObject.setCreatedAt(new Date());

                            // There is no information from the specification about a valid expiration...
                            Instant expirationInst = requestObject.getCreatedAt().toInstant().plus(Duration.ofDays(1));
                            requestObject.setExpireAt(Date.from(expirationInst));

                            requestObject.setPayload(request.getRequest());

                            return requestObjectRepository.create(requestObject);
                        }
                    })
                    .flatMap((Function<RequestObject, SingleSource<RequestObjectRegistrationResponse>>) requestObject -> {
                        RequestObjectRegistrationResponse response = new RequestObjectRegistrationResponse();

                        response.setIss(openIDDiscoveryService.getIssuer(request.getOrigin()));
                        response.setAud(client.getClientId());
                        response.setRequestUri(RESOURCE_OBJECT_URN_PREFIX + requestObject.getId());
                        response.setExp(requestObject.getExpireAt().getTime());

                        return Single.just(response);
                    });
        } catch (ParseException pe) {
            return Single.error(new InvalidRequestObjectException());
        }
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
}
