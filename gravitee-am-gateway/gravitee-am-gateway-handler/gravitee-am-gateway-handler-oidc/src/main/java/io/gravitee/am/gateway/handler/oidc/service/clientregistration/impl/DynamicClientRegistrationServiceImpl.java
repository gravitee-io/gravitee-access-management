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
package io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.oidc.service.utils.JWAlgorithmUtils;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.DynamicClientRegistrationRequest;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.DynamicClientRegistrationService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.utils.SubjectTypeUtils;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.exception.InvalidRedirectUriException;
import io.gravitee.am.service.utils.GrantTypeUtils;
import io.gravitee.am.service.utils.ResponseTypeUtils;
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
import java.util.*;

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
    private ClientService clientService;

    @Autowired
    private JWKService jwkService;

    @Autowired
    private JWTService jwtService;

    @Autowired
    public WebClient client;

    @Autowired
    private Domain domain;

    @Autowired
    private FormService formService;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Override
    public Single<Client> create(DynamicClientRegistrationRequest request, String basePath) {
        //If Dynamic client registration from template is enabled and request contains a software_id
        if(domain.isDynamicClientRegistrationTemplateEnabled() && request.getSoftwareId()!=null && request.getSoftwareId().isPresent()) {
            return this.createClientFromTemplate(request, basePath);
        }
        return this.createClientFromRequest(request,basePath);
    }

    @Override
    public Single<Client> patch(Client toPatch, DynamicClientRegistrationRequest request, String basePath) {
        return this.validateClientPatchRequest(request)
                .map(req -> req.patch(toPatch))
                .flatMap(app -> this.applyRegistrationAccessToken(basePath, app))
                .flatMap(clientService::update);
    }

    @Override
    public Single<Client> update(Client toUpdate, DynamicClientRegistrationRequest request, String basePath) {
        return this.validateClientRegistrationRequest(request)
                .map(req -> req.patch(toUpdate))
                .flatMap(app -> this.applyRegistrationAccessToken(basePath, app))
                .flatMap(clientService::update);
    }

    @Override
    public Single<Client> delete(Client toDelete) {
        return this.clientService.delete(toDelete.getId()).toSingleDefault(toDelete);
    }

    @Override
    public Single<Client> renewSecret(Client toRenew, String basePath) {
        return clientService.renewClientSecret(domain.getId(), toRenew.getId())
                // after each modification we must update the registration token
                .flatMap(client -> applyRegistrationAccessToken(basePath, client))
                .flatMap(clientService::update);
    }

    private Single<Client> createClientFromRequest(DynamicClientRegistrationRequest request, String basePath) {
        Client client = new Client();
        client.setClientId(SecureRandomString.generate());
        client.setDomain(domain.getId());

        return this.validateClientRegistrationRequest(request)
                .map(req -> req.patch(client))
                .flatMap(this::applyDefaultIdentityProvider)
                .flatMap(this::applyDefaultCertificateProvider)
                .flatMap(app -> this.applyRegistrationAccessToken(basePath, app))
                .flatMap(clientService::create);
    }

    /**
     * <pre>
     * Software_id is based on id field and not client_id because:
     * this field is not intended to be human readable and is usually opaque to the client and authorization server.
     * the client may switch back from template to real client and then this is better to not expose it's client_id.
     * @param request
     * @param basePath
     * @return
     * </pre>
     */
    private Single<Client> createClientFromTemplate(DynamicClientRegistrationRequest request, String basePath) {
        return clientService.findById(request.getSoftwareId().get())
                .switchIfEmpty(Maybe.error(new InvalidClientMetadataException("No template found for software_id "+request.getSoftwareId().get())))
                .flatMapSingle(this::sanitizeTemplate)
                .map(request::patch)
                .flatMap(app -> this.applyRegistrationAccessToken(basePath, app))
                .flatMap(clientService::create)
                .flatMap(client -> copyForms(request.getSoftwareId().get(),client))
                .flatMap(client -> copyEmails(request.getSoftwareId().get(),client));
    }

    private Single<Client> sanitizeTemplate(Client template) {
        if(!template.isTemplate()) {
            return Single.error(new InvalidClientMetadataException("Client behind software_id is not a template"));
        }
        //Erase potential confidential values.
        template.setClientId(SecureRandomString.generate());
        template.setDomain(domain.getId());
        template.setId(null);
        template.setClientSecret(null);
        template.setClientName(null);
        template.setRedirectUris(null);
        template.setSectorIdentifierUri(null);
        template.setJwks(null);
        template.setJwksUri(null);
        //Set it as non template
        template.setTemplate(false);

        return Single.just(template);
    }

    private Single<Client> copyForms(String sourceId, Client client) {
        return formService.copyFromClient(domain.getId(), sourceId, client.getId())
                .flatMap(irrelevant -> Single.just(client));
    }

    private Single<Client> copyEmails(String sourceId, Client client) {
        return emailTemplateService.copyFromClient(domain.getId(), sourceId, client.getId())
                .flatMap(irrelevant -> Single.just(client));
    }

    /**
     * Identity provider is not part of dynamic client registration but needed on the client.
     * So we set the first identoty provider available on the domain.
     * @param client App to create
     * @return
     */
    private Single<Client> applyDefaultIdentityProvider(Client client) {
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
    private Single<Client> applyDefaultCertificateProvider(Client client) {
        return certificateService.findByDomain(client.getDomain())
                .map(certificates -> {
                    if(certificates!=null && !certificates.isEmpty()) {
                        client.setCertificate(certificates.get(0).getId());
                    }
                    return client;
                });
    }

    private Single<Client> applyRegistrationAccessToken(String basePath, Client client) {

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
    private Single<DynamicClientRegistrationRequest> validateClientRegistrationRequest(final DynamicClientRegistrationRequest request) {
        LOGGER.debug("Validating dynamic client registration payload");
        return this.validateClientRegistrationRequest(request,false);
    }

    private Single<DynamicClientRegistrationRequest> validateClientPatchRequest(DynamicClientRegistrationRequest request) {
        LOGGER.debug("Validating dynamic client registration payload : patch");
        //redirect_uri is mandatory in the request, but in case of patch we may ommit it...
        return this.validateClientRegistrationRequest(request,true);
    }

    private Single<DynamicClientRegistrationRequest> validateClientRegistrationRequest(final DynamicClientRegistrationRequest request, boolean isPatch) {
        if(request==null) {
            return Single.error(new InvalidClientMetadataException());
        }

        return this.validateRedirectUri(request, isPatch)
                .flatMap(this::validateScopes)
                .flatMap(this::validateGrantType)
                .flatMap(this::validateResponseType)
                .flatMap(this::validateSubjectType)
                .flatMap(this::validateRequestUri)
                .flatMap(this::validateSectorIdentifierUri)
                .flatMap(this::validateJKWs)
                .flatMap(this::validateUserinfoSigningAlgorithm)
                .flatMap(this::validateUserinfoEncryptionAlgorithm)
                .flatMap(this::validateIdTokenSigningAlgorithm)
                .flatMap(this::validateIdTokenEncryptionAlgorithm)
                .flatMap(this::validateTlsClientAuth)
                .flatMap(this::validateSelfSignedClientAuth);
    }

    /**
     * According to openid specification, redirect_uris are REQUIRED in the request for creation.
     * But according to the grant type, it may be null or empty.
     * @param request DynamicClientRegistrationRequest
     * @param isPatch true if only updating some fields (else means all fields will overwritten)
     * @return DynamicClientRegistrationRequest
     */
    private Single<DynamicClientRegistrationRequest> validateRedirectUri(DynamicClientRegistrationRequest request, boolean isPatch) {

        //Except for patching a client, redirect_uris metadata is required but may be null or empty.
        if(!isPatch && request.getRedirectUris() == null) {
            return Single.error(new InvalidRedirectUriException());
        }

        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateResponseType(DynamicClientRegistrationRequest request) {
        //if response_type provided, they must be valid.
        if(request.getResponseTypes()!=null) {
            if(!ResponseTypeUtils.isSupportedResponseType(request.getResponseTypes().orElse(Collections.emptyList()))) {
                return Single.error(new InvalidClientMetadataException("Invalid response type."));
            }
        }
        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateGrantType(DynamicClientRegistrationRequest request) {
        //if grant_type provided, they must be valid.
        if(request.getGrantTypes()!=null) {
            if (!GrantTypeUtils.isSupportedGrantType(request.getGrantTypes().orElse(Collections.emptyList()))) {
                return Single.error(new InvalidClientMetadataException("Missing or invalid grant type."));
            }
        }
        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateSubjectType(DynamicClientRegistrationRequest request) {
        //if subject_type is provided, it must be valid.
        if(request.getSubjectType()!=null && request.getSubjectType().isPresent()) {
            if(!SubjectTypeUtils.isValidSubjectType(request.getSubjectType().get())) {
                return Single.error(new InvalidClientMetadataException("Unsupported subject type"));
            }
        }
        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateUserinfoSigningAlgorithm(DynamicClientRegistrationRequest request) {
        //if userinfo_signed_response_alg is provided, it must be valid.
        if(request.getUserinfoSignedResponseAlg()!=null && request.getUserinfoSignedResponseAlg().isPresent()) {
            if(!JWAlgorithmUtils.isValidUserinfoSigningAlg(request.getUserinfoSignedResponseAlg().get())) {
                return Single.error(new InvalidClientMetadataException("Unsupported userinfo signing algorithm"));
            }
        }
        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateUserinfoEncryptionAlgorithm(DynamicClientRegistrationRequest request) {
        if(request.getUserinfoEncryptedResponseEnc()!=null && request.getUserinfoEncryptedResponseAlg()==null) {
            return Single.error(new InvalidClientMetadataException("When userinfo_encrypted_response_enc is included, userinfo_encrypted_response_alg MUST also be provided"));
        }
        //if userinfo_encrypted_response_alg is provided, it must be valid.
        if(request.getUserinfoEncryptedResponseAlg()!=null && request.getUserinfoEncryptedResponseAlg().isPresent()) {
            if(!JWAlgorithmUtils.isValidUserinfoResponseAlg(request.getUserinfoEncryptedResponseAlg().get())) {
                return Single.error(new InvalidClientMetadataException("Unsupported userinfo_encrypted_response_alg value"));
            }
            if(request.getUserinfoEncryptedResponseEnc()!=null && request.getUserinfoEncryptedResponseEnc().isPresent()) {
                if(!JWAlgorithmUtils.isValidUserinfoResponseEnc(request.getUserinfoEncryptedResponseEnc().get())) {
                    return Single.error(new InvalidClientMetadataException("Unsupported userinfo_encrypted_response_enc value"));
                }
            }
            else {
                //Apply default value if userinfo_encrypted_response_alg is informed and not userinfo_encrypted_response_enc.
                request.setUserinfoEncryptedResponseEnc(Optional.of(JWAlgorithmUtils.getDefaultUserinfoResponseEnc()));
            }
        }
        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateIdTokenSigningAlgorithm(DynamicClientRegistrationRequest request) {
        //if userinfo_signed_response_alg is provided, it must be valid.
        if(request.getIdTokenSignedResponseAlg()!=null && request.getIdTokenSignedResponseAlg().isPresent()) {
            if(!JWAlgorithmUtils.isValidIdTokenSigningAlg(request.getIdTokenSignedResponseAlg().get())) {
                return Single.error(new InvalidClientMetadataException("Unsupported id_token signing algorithm"));
            }
        }
        return Single.just(request);
    }

    private Single<DynamicClientRegistrationRequest> validateIdTokenEncryptionAlgorithm(DynamicClientRegistrationRequest request) {
        if(request.getIdTokenEncryptedResponseEnc()!=null && request.getIdTokenEncryptedResponseAlg()==null) {
            return Single.error(new InvalidClientMetadataException("When id_token_encrypted_response_enc is included, id_token_encrypted_response_alg MUST also be provided"));
        }
        //if id_token_encrypted_response_alg is provided, it must be valid.
        if(request.getIdTokenEncryptedResponseAlg()!=null && request.getIdTokenEncryptedResponseAlg().isPresent()) {
            if(!JWAlgorithmUtils.isValidIdTokenResponseAlg(request.getIdTokenEncryptedResponseAlg().get())) {
                return Single.error(new InvalidClientMetadataException("Unsupported id_token_encrypted_response_alg value"));
            }
            if(request.getIdTokenEncryptedResponseEnc()!=null && request.getIdTokenEncryptedResponseEnc().isPresent()) {
                if(!JWAlgorithmUtils.isValidIdTokenResponseEnc(request.getIdTokenEncryptedResponseEnc().get())) {
                    return Single.error(new InvalidClientMetadataException("Unsupported id_token_encrypted_response_enc value"));
                }
            }
            else {
                //Apply default value if id_token_encrypted_response_alg is informed and not id_token_encrypted_response_enc.
                request.setIdTokenEncryptedResponseEnc(Optional.of(JWAlgorithmUtils.getDefaultIdTokenResponseEnc()));
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
                    .switchIfEmpty(Maybe.error(new InvalidClientMetadataException("No JWK found behind jws uri...")))
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
     * Remove non allowed scopes (if feature is enabled) and then apply default scopes.
     * The scopes validations are done later (validateMetadata) on the process.
     * @param request DynamicClientRegistrationRequest
     * @return DynamicClientRegistrationRequest
     */
    private Single<DynamicClientRegistrationRequest> validateScopes(DynamicClientRegistrationRequest request) {

        final boolean hasAllowedScopes = domain.getOidc()!=null && domain.getOidc().getClientRegistrationSettings()!=null &&
                domain.getOidc().getClientRegistrationSettings().isAllowedScopesEnabled() &&
                domain.getOidc().getClientRegistrationSettings().getAllowedScopes()!=null;

        final boolean hasDefaultScopes = domain.getOidc()!=null && domain.getOidc().getClientRegistrationSettings()!=null &&
                domain.getOidc().getClientRegistrationSettings().getDefaultScopes()!=null &&
                !domain.getOidc().getClientRegistrationSettings().getDefaultScopes().isEmpty();

        //Remove from the request every non allowed scope
        if(request.getScope()!=null && request.getScope().isPresent() && hasAllowedScopes) {

            final Set<String> allowedScopes = new HashSet<>(domain.getOidc().getClientRegistrationSettings().getAllowedScopes());
            final Set<String> requestedScopes = new HashSet<>(request.getScope().get());

            //Remove non allowed scope
            requestedScopes.retainAll(allowedScopes);

            //Update the request
            request.setScope(Optional.of(String.join(SCOPE_DELIMITER,requestedScopes)));
        }

        //Apply default scope if scope metadata is empty
        if((request.getScope()==null || !request.getScope().isPresent() || request.getScope().get().isEmpty()) && hasDefaultScopes) {
            //Add default scopes if needed
            request.setScope(Optional.of(String.join(SCOPE_DELIMITER,domain.getOidc().getClientRegistrationSettings().getDefaultScopes())));
        }

        return Single.just(request);
    }

    /**
     * <p>
     *    A client using the "tls_client_auth" authentication method MUST use exactly one of the
     *    below metadata parameters to indicate the certificate subject value that the authorization server is
     *    to expect when authenticating the respective client.
     * </p>
     * <a href="https://tools.ietf.org/html/rfc8705#section-2.1.2">Client Registration Metadata</a>
     *
     * @param request DynamicClientRegistrationRequest
     * @return DynamicClientRegistrationRequest
     */
    private Single<DynamicClientRegistrationRequest> validateTlsClientAuth(DynamicClientRegistrationRequest request) {
        if(request.getTokenEndpointAuthMethod() != null &&
                request.getTokenEndpointAuthMethod().isPresent() &&
                ClientAuthenticationMethod.TLS_CLIENT_AUTH.equalsIgnoreCase(request.getTokenEndpointAuthMethod().get())) {

            if ((request.getTlsClientAuthSubjectDn() == null || ! request.getTlsClientAuthSubjectDn().isPresent()) &&
                    (request.getTlsClientAuthSanDns() == null || ! request.getTlsClientAuthSanDns().isPresent()) &&
                    (request.getTlsClientAuthSanIp() == null || ! request.getTlsClientAuthSanIp().isPresent()) &&
                    (request.getTlsClientAuthSanEmail() == null || ! request.getTlsClientAuthSanEmail().isPresent()) &&
                    (request.getTlsClientAuthSanUri() == null || ! request.getTlsClientAuthSanUri().isPresent())) {
                return Single.error(new InvalidClientMetadataException("Missing TLS parameter for tls_client_auth."));
            }

            if (request.getTlsClientAuthSubjectDn() != null && request.getTlsClientAuthSubjectDn().isPresent() && (
                    request.getTlsClientAuthSanDns().isPresent() || request.getTlsClientAuthSanEmail().isPresent() ||
                            request.getTlsClientAuthSanIp().isPresent() || request.getTlsClientAuthSanUri().isPresent())) {
                return Single.error(new InvalidClientMetadataException("The tls_client_auth must use exactly one of the TLS parameters."));
            } else if (request.getTlsClientAuthSanDns() != null && request.getTlsClientAuthSanDns().isPresent() && (
                    request.getTlsClientAuthSubjectDn().isPresent() || request.getTlsClientAuthSanEmail().isPresent() ||
                            request.getTlsClientAuthSanIp().isPresent() || request.getTlsClientAuthSanUri().isPresent())) {
                return Single.error(new InvalidClientMetadataException("The tls_client_auth must use exactly one of the TLS parameters."));
            } else if (request.getTlsClientAuthSanIp() != null && request.getTlsClientAuthSanIp().isPresent() && (
                    request.getTlsClientAuthSubjectDn().isPresent() || request.getTlsClientAuthSanDns().isPresent() ||
                            request.getTlsClientAuthSanEmail().isPresent() || request.getTlsClientAuthSanUri().isPresent())) {
                return Single.error(new InvalidClientMetadataException("The tls_client_auth must use exactly one of the TLS parameters."));
            } else if (request.getTlsClientAuthSanEmail() != null && request.getTlsClientAuthSanEmail().isPresent() && (
                    request.getTlsClientAuthSubjectDn().isPresent() || request.getTlsClientAuthSanDns().isPresent() ||
                            request.getTlsClientAuthSanIp().isPresent() || request.getTlsClientAuthSanUri().isPresent())) {
                return Single.error(new InvalidClientMetadataException("The tls_client_auth must use exactly one of the TLS parameters."));
            } else if (request.getTlsClientAuthSanUri() != null && request.getTlsClientAuthSanUri().isPresent() && (
                    request.getTlsClientAuthSubjectDn().isPresent() || request.getTlsClientAuthSanDns().isPresent() ||
                            request.getTlsClientAuthSanIp().isPresent() || request.getTlsClientAuthSanEmail().isPresent())) {
                return Single.error(new InvalidClientMetadataException("The tls_client_auth must use exactly one of the TLS parameters."));
            }
        }

        return Single.just(request);
    }

    /**
     * <p>
     *    This method of mutual-TLS OAuth client authentication is intended to
     *    support client authentication using self-signed certificates.  As a
     *    prerequisite, the client registers its X.509 certificates (using
     *    "jwks" defined in [RFC7591]) or a reference to a trusted source for
     *    its X.509 certificates (using "jwks_uri" from [RFC7591]) with the
     *    authorization server.
     * </p>
     * <a href="https://tools.ietf.org/html/rfc8705#section-2.2.2">Client Registration Metadata</a>
     *
     * @param request DynamicClientRegistrationRequest
     * @return DynamicClientRegistrationRequest
     */
    private Single<DynamicClientRegistrationRequest> validateSelfSignedClientAuth(DynamicClientRegistrationRequest request) {
        if (request.getTokenEndpointAuthMethod() != null &&
                request.getTokenEndpointAuthMethod().isPresent() &&
                ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH.equalsIgnoreCase(request.getTokenEndpointAuthMethod().get())) {
            if ((request.getJwks() == null || !request.getJwks().isPresent()) &&
                    (request.getJwksUri() == null || !request.getJwksUri().isPresent())) {
                return Single.error(new InvalidClientMetadataException("The self_signed_tls_client_auth requires at least a jwks or a valid jwks_uri."));
            }
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
