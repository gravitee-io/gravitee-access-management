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
package io.gravitee.am.gateway.handler.oidc.clientregistration.impl;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.gateway.handler.jwk.JwkService;
import io.gravitee.am.gateway.handler.jwt.JwtService;
import io.gravitee.am.gateway.handler.oidc.clientregistration.DynamicClientRegistrationService;
import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.request.DynamicClientRegistrationRequest;
import io.gravitee.am.gateway.handler.oidc.utils.SigningAlgorithmUtils;
import io.gravitee.am.gateway.handler.oidc.utils.SubjectTypeUtils;
import io.gravitee.am.model.Client;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.exception.InvalidRedirectUriException;
import io.gravitee.am.service.utils.GrantTypeUtils;
import io.gravitee.am.service.utils.ResponseTypeUtils;
import io.gravitee.am.service.utils.UriBuilder;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static io.gravitee.am.common.oidc.Scope.SCOPE_DELIMITER;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class DynamicClientRegistrationServiceImpl implements DynamicClientRegistrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicClientRegistrationServiceImpl.class);

    @Autowired
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private JwkService jwkService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    public WebClient client;

    @Override
    public Client create(String domain, DynamicClientRegistrationRequest request) {
        Client client = new Client();
        client.setClientId(SecureRandomString.generate());
        client.setDomain(domain);
        return request.patch(client);
    }

    /**
     * Identity provider is not part of dynamic client registration but needed on the client.
     * So we set the first identoty provider available on the domain.
     * @param client App to create
     * @return
     */
    @Override
    public Single<Client> applyDefaultIdentityProvider(Client client) {
        return identityProviderService.findByDomain(client.getDomain())
            .map(identityProviders -> {
                if(identityProviders!=null && !identityProviders.isEmpty()) {
                    client.setIdentities(Collections.singleton(identityProviders.get(0).getId()));
                }
                return client;
            });
    }

    /**
     * Certificate provider is not part of dynamic client registration but needed on the client.
     * So we set the first certificate provider available on the domain.
     * @param client App to create
     * @return
     */
    @Override
    public Single<Client> applyDefaultCertificateProvider(Client client) {
        return certificateService.findByDomain(client.getDomain())
                .map(certificates -> {
                    if(certificates!=null && !certificates.isEmpty()) {
                        client.setCertificate(certificates.get(0).getId());
                    }
                    return client;
                });
    }

    @Override
    public Single<Client> applyRegistrationAccessToken(String basePath, Client client) {

        OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration(basePath);

        JWT jwt = new JWT();
        jwt.setIss(openIDProviderMetadata.getIssuer());
        jwt.setSub(client.getClientId());
        jwt.setAud(client.getClientId());
        jwt.setDomain(client.getDomain());
        jwt.setIat(new Date().getTime() / 1000l);
        jwt.setExp(Date.from(new Date().toInstant().plusSeconds(3600*24*365*2)).getTime() / 1000l);
        jwt.setScope(Scope.DCR.getKey());
        jwt.setJti(SecureRandomString.generate());

        return jwtService.encode(jwt, client)
                .map(token -> {
                    client.setRegistrationAccessToken(token);
                    client.setRegistrationClientUri(openIDProviderMetadata.getRegistrationEndpoint()+"/"+client.getClientId());
                    return client;
                });
    }

    /**
     * Validate payload according to openid specifications.
     *
     * https://openid.net/specs/openid-connect-registration-1_0.html#ClientMetadata
     *
     * @param request DynamicClientRegistrationRequest
     */
    @Override
    public Single<DynamicClientRegistrationRequest> validateClientRegistrationRequest(final DynamicClientRegistrationRequest request) {
        LOGGER.debug("Validating dynamic client registration payload");

        if(request==null) {
            return Single.error(new InvalidClientMetadataException());
        }

        return this.validateRedirectUri(request)
                .flatMap(this::validateResponseType)
                .flatMap(this::validateGrantType)
                .flatMap(this::validateSubjectType)
                .flatMap(this::validateRequestUri)
                .flatMap(this::validateSectorIdentifierUri)
                .flatMap(this::validateJKWs)
                .flatMap(this::validateUserinfoSigningAlgorithm)
                .flatMap(this::validateScopes);
    }

    @Override
    public Single<DynamicClientRegistrationRequest> validateClientPatchRequest(DynamicClientRegistrationRequest request) {
        LOGGER.debug("Validating dynamic client registration payload : patch");

        if(request==null) {
            return Single.error(new InvalidClientMetadataException());
        }

        return this.validateResponseType(request)
                .flatMap(this::validateGrantType)
                .flatMap(this::validateSubjectType)
                .flatMap(this::validateRequestUri)
                .flatMap(this::validateSectorIdentifierUri)
                .flatMap(this::validateJKWs)
                .flatMap(this::validateUserinfoSigningAlgorithm);
    }

    private Single<DynamicClientRegistrationRequest> validateRedirectUri(DynamicClientRegistrationRequest request) {
        //Redirect_uri is required, must be informed and filled without null values.
        if(request.getRedirectUris()==null || !request.getRedirectUris().isPresent() || request.getRedirectUris().get().isEmpty()) {
            return Single.error(new InvalidRedirectUriException());
        }
        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateResponseType(DynamicClientRegistrationRequest request) {
        //if response_type provided, they must be valid.
        if(request.getResponseTypes()!=null) {
            if(!ResponseTypeUtils.isValidResponseType(request.getResponseTypes().get())) {
                return Single.error(new InvalidClientMetadataException("Invalid response type."));
            }
        }
        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateGrantType(DynamicClientRegistrationRequest request) {
        //if grant_type provided, they must be valid.
        if(request.getGrantTypes()!=null) {
            if(!GrantTypeUtils.isValidGrantType(request.getGrantTypes().get())) {
                return Single.error(new InvalidClientMetadataException("Missing or invalid grant type."));
            }
        }
        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateSubjectType(DynamicClientRegistrationRequest request) {
        //if subject_type is provided, it must be valid.
        if(request.getSubjectType()!=null) {
            if(!SubjectTypeUtils.isValidSubjectType(request.getSubjectType().get())) {
                return Single.error(new InvalidClientMetadataException("Unsupported subject type"));
            }
        }
        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateUserinfoSigningAlgorithm(DynamicClientRegistrationRequest request) {
        //if userinfo_signed_response_alg is provided, it must be valid.
        if(request.getUserinfoSignedResponseAlg()!=null) {
            if(!SigningAlgorithmUtils.isValidUserinfoSigningAlg(request.getUserinfoSignedResponseAlg().get())) {
                return Single.error(new InvalidClientMetadataException("Unsupported userinfo signing algorithm"));
            }
        }
        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateRequestUri(DynamicClientRegistrationRequest request) {
        //Check request_uri well formated
        if(request.getRequestUris()!=null && request.getRequestUris().isPresent()) {
            try {
                //throw exception if uri mal formated
                request.getRequestUris().get().stream().forEach(this::formatUrl);
            } catch (InvalidClientMetadataException err) {
                return Single.error(new InvalidClientMetadataException("request_uris: "+err.getMessage()));
            }
        }
        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateSectorIdentifierUri(DynamicClientRegistrationRequest request) {
        //if sector_identifier_uri is provided, then retrieve content and validate redirect_uris among this list.
        if(request.getSectorIdentifierUri()!=null && request.getSectorIdentifierUri().isPresent()) {

            URI uri;
            try {
                //throw exception if uri mal formated
                uri = formatUrl(request.getSectorIdentifierUri().get());
            } catch (InvalidClientMetadataException err) {
                return Single.error(new InvalidClientMetadataException("sector_identifier_uri: "+err.getMessage()));
            }

            if(!uri.getScheme().equalsIgnoreCase("https")) {
                return Single.error(new InvalidClientMetadataException("Scheme must be https for sector_identifier_uri : "+request.getSectorIdentifierUri().get()));
            }

            return client.getAbs(uri.toString())
                    .rxSend()
                    .map(HttpResponse::bodyAsString)
                    .map(JsonArray::new)
                    .onErrorResumeNext(Single.error(new InvalidClientMetadataException("Unable to parse sector_identifier_uri : "+ uri.toString())))
                    .flatMapPublisher(Flowable::fromIterable)
                    .cast(String.class)
                    .collect(HashSet::new,(set, value)->set.add(value))
                    .flatMap(allowedRedirectUris -> Observable.fromIterable(request.getRedirectUris().get())
                            .filter(redirectUri -> !allowedRedirectUris.contains(redirectUri))
                            .collect(ArrayList<String>::new, (list, missingRedirectUri)-> list.add(missingRedirectUri))
                            .flatMap(missing -> {
                                if(!missing.isEmpty()) {
                                    return Single.error(
                                            new InvalidRedirectUriException("redirect uris are not allowed according to sector_identifier_uri: "+
                                                    String.join(" ",missing)
                                            )
                                    );
                                } else {
                                    return Single.just(request);
                                }
                            })
                    );
        }
        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateJKWs(DynamicClientRegistrationRequest request) {
        //The jwks_uri and jwks parameters MUST NOT be used together.
        if(request.getJwks()!=null && request.getJwks().isPresent() && request.getJwksUri()!=null && request.getJwksUri().isPresent()) {
            return Single.error(new InvalidClientMetadataException("The jwks_uri and jwks parameters MUST NOT be used together."));
        }

        //Check jwks_uri
        if(request.getJwksUri()!=null && request.getJwksUri().isPresent()) {
            return jwkService.getKeys(request.getJwksUri().get())
                    .switchIfEmpty(Maybe.error(new InvalidClientMetadataException("No JWK found behing jws uri...")))
                    .flatMapSingle(jwkSet -> {
                        /* Uncomment if we expect to save it as fallback
                        if(jwkSet!=null && jwkSet.isPresent()) {
                            request.setJwks(jwkSet);
                        }
                        */
                        return Single.just(request);
                    });
        }

        return Single.just(request);
    }

    /**
     * Here the target is not to validate all scopes but ensure that openid is well included.
     * The scopes validations are done later (validateMetadata) on the process.
     * @param request DynamicClientRegistrationRequest
     * @return DynamicClientRegistrationRequest
     */
    private Single<DynamicClientRegistrationRequest> validateScopes(DynamicClientRegistrationRequest request) {

        if(request.getScope()!=null && request.getScope().isPresent()) {
            if(request.getScope().get().stream().filter(Scope.OPENID.getKey()::equals).count()==0) {
                List scopes = new LinkedList(request.getScope().get());
                scopes.add(Scope.OPENID.getKey());
                request.setScope(Optional.of(String.join(SCOPE_DELIMITER,scopes)));
            }
        } else {
            request.setScope(Optional.of(Scope.OPENID.getKey()));
        }

        return Single.just(request);
    }

    /**
     * Check Uri is well formatted
     * @param uri String
     * @return URI if well formatted, else throw an InvalidClientMetadataException
     */
    private URI formatUrl(String uri) {
        try {
            return UriBuilder.fromHttpUrl(uri).build();
        }
        catch(IllegalArgumentException | URISyntaxException ex) {
            throw new InvalidClientMetadataException(uri+" is not valid.");
        }
    }
}
