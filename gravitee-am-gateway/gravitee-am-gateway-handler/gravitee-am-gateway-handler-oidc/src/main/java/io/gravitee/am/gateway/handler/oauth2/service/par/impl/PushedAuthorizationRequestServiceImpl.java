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
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestObjectException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestUriException;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oauth2.service.par.PushedAuthorizationRequestResponse;
import io.gravitee.am.gateway.handler.oauth2.service.par.PushedAuthorizationRequestService;
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
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.functions.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static io.gravitee.am.common.oidc.ClientAuthenticationMethod.JWT_BEARER;
import static io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.ParamUtils.redirectMatches;
import static io.gravitee.am.gateway.handler.oidc.service.utils.JWAlgorithmUtils.isSignAlgCompliantWithFapi;

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
        par.setDomain(domain.getId());; // link parameters to the internal domain identifier
        par.setExpireAt(new Date(Instant.now().plusMillis(requestUriValidity).toEpochMilli()));



        Completable registrationValidation = Completable.fromAction(() -> {
            String clientId = jwtClientAssertion(par) ? getClientIdFromAssertion(par) : par.getParameters().getFirst(Parameters.CLIENT_ID);
            if (!client.getClientId().equals(clientId)) {
               throw new InvalidRequestException();
            }
            if (par.getParameters().getFirst(io.gravitee.am.common.oidc.Parameters.REQUEST_URI) != null) {
                throw new InvalidRequestException("request_uri not authorized");
            }
        });

        final String request = par.getParameters().getFirst(io.gravitee.am.common.oidc.Parameters.REQUEST);
        if (request != null) {
            registrationValidation = registrationValidation
                    .andThen(Single.defer(() ->
                            readRequestObject(client, request)
                            .map(jwt -> checkRedirectUriParameter(jwt, client))))
                    .ignoreElement();
        } else {
            registrationValidation.andThen(Completable.fromAction(() -> checkRedirectUriParameter(par, client)));
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

    private boolean jwtClientAssertion(PushedAuthorizationRequest par) {
        return par.getParameters().getFirst(Parameters.CLIENT_ASSERTION) != null && JWT_BEARER.equals(par.getParameters().getFirst(Parameters.CLIENT_ASSERTION_TYPE));
    }

    private String getClientIdFromAssertion(PushedAuthorizationRequest par) {
        try {
            return JWTParser.parse(par.getParameters().getFirst(Parameters.CLIENT_ASSERTION)).getJWTClaimsSet().getSubject();
        } catch (ParseException e) {
            LOGGER.warn("Unable to parse the Client Assertion to extract the sub claim");
            return null;
        }
    }

    private Single<JWT> readRequestObject(Client client, String request) {
        return jweService.decrypt(request, false)
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

        if (this.domain.usePlainFapiProfile() && !isSignAlgCompliantWithFapi(jwt.getHeader().getAlgorithm().getName())) {
            throw new InvalidRequestObjectException("Request object must be signed with PS256");
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
                .switchIfEmpty(Maybe.error(new InvalidRequestObjectException("Invalid key ID")))
                .flatMapSingle(new Function<JWK, SingleSource<JWT>>() {
                    @Override
                    public SingleSource<JWT> apply(JWK jwk) throws Exception {
                        // 6.3.2.  Signed Request Object
                        // To perform Signature Validation, the alg Header Parameter in the
                        // JOSE Header MUST match the value of the request_object_signing_alg
                        // set during Client Registration
                        if (!jwt.getHeader().getAlgorithm().getName().equals(client.getRequestObjectSigningAlg())) {
                            return Single.error(new InvalidRequestObjectException("Invalid request object signing algorithm"));
                        } else if (jwsService.isValidSignature(jwt, jwk)) {
                            return Single.just(jwt);
                        } else {
                            return Single.error(new InvalidRequestObjectException("Invalid signature"));
                        }
                    }
                });
    }

    private PushedAuthorizationRequest checkRedirectUriParameter(PushedAuthorizationRequest request, Client client) {
        checkRedirectUri(client, request.getParameters().getFirst(Parameters.REDIRECT_URI));
        return request;
    }

    private JWT checkRedirectUriParameter(JWT request, Client client) {
        try {
            String requestedRedirectUri = request.getJWTClaimsSet().getStringClaim(Parameters.REDIRECT_URI);
            checkRedirectUri(client, requestedRedirectUri);
        } catch (ParseException e) {
            throw new InvalidRequestException("request object is malformed");
        }
        return request;
    }

    private void checkRedirectUri(Client client, String requestedRedirectUri) {
        final List<String> registeredClientRedirectUris = client.getRedirectUris();
        final boolean hasRegisteredClientRedirectUris = registeredClientRedirectUris != null && !registeredClientRedirectUris.isEmpty();
        final boolean hasRequestedRedirectUri = requestedRedirectUri != null && !requestedRedirectUri.isEmpty();

        // if no requested redirect_uri and no registered client redirect_uris
        // throw invalid request exception
        if (!hasRegisteredClientRedirectUris && !hasRequestedRedirectUri) {
            throw new InvalidRequestException("A redirect_uri must be supplied");
        }

        // if no requested redirect_uri and more than one registered client redirect_uris
        // throw invalid request exception
        if (!hasRequestedRedirectUri && (registeredClientRedirectUris != null && registeredClientRedirectUris.size() > 1)) {
            throw new InvalidRequestException("Unable to find suitable redirect_uri, a redirect_uri must be supplied");
        }

        // if requested redirect_uri doesn't match registered client redirect_uris
        // throw redirect mismatch exception
        if (hasRequestedRedirectUri && hasRegisteredClientRedirectUris) {
            checkMatchingRedirectUri(requestedRedirectUri, registeredClientRedirectUris);
        }
    }

    private void checkMatchingRedirectUri(String requestedRedirect, List<String> registeredClientRedirectUris) {
        if (registeredClientRedirectUris
                .stream()
                .noneMatch(registeredClientUri -> redirectMatches(requestedRedirect, registeredClientUri, this.domain.isRedirectUriStrictMatching() || this.domain.usePlainFapiProfile()))) {
            throw new InvalidRequestObjectException("The redirect_uri MUST match the registered callback URL for this application");
        }
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