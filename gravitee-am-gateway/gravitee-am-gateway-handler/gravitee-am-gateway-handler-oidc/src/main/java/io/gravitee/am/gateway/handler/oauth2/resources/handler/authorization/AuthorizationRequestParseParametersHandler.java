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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.CodeChallengeMethod;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oauth2.exception.LoginRequiredException;
import io.gravitee.am.gateway.handler.oauth2.exception.RedirectMismatchException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedResponseModeException;
import io.gravitee.am.gateway.handler.oauth2.service.pkce.PKCEUtils;
import io.gravitee.am.gateway.handler.oidc.exception.ClaimsRequestSyntaxException;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.request.ClaimRequest;
import io.gravitee.am.gateway.handler.oidc.service.request.ClaimsRequest;
import io.gravitee.am.gateway.handler.oidc.service.request.ClaimsRequestResolver;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static io.gravitee.am.common.utils.ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.REQUEST_OBJECT_FROM_URI;
import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.getOAuthParameter;
import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.redirectMatches;
import static io.gravitee.am.service.utils.ResponseTypeUtils.requireNonce;

/**
 * The authorization server validates the request to ensure that all parameters are valid.
 * If the request is valid, the authorization server authenticates the resource owner and obtains
 * an authorization decision (by asking the resource owner or by establishing approval via other means).
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.1.1">4.1.1. Authorization Request</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestParseParametersHandler extends AbstractAuthorizationRequestHandler implements Handler<RoutingContext> {

    private final ClaimsRequestResolver claimsRequestResolver = new ClaimsRequestResolver();
    private final Domain domain;

    public AuthorizationRequestParseParametersHandler(Domain domain) {
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        // proceed prompt parameter
        parsePromptParameter(context);

        // proceed pkce parameter
        parsePKCEParameter(context, client);

        // proceed max_age parameter
        parseMaxAgeParameter(context);

        // proceed claims parameter
        parseClaimsParameter(context);

        // proceed response mode parameter
        parseResponseModeParameter(context);

        // proceed nonce parameter
        parseNonceParameter(context);

        // proceed grant_type parameter
        parseGrantTypeParameter(client);

        // proceed response_type parameter
        parseResponseTypeParameter(context, client);

        // proceed redirect_uri parameter
        parseRedirectUriParameter(context, client);

        context.next();
    }

    private void parsePromptParameter(RoutingContext context) {
        String prompt = getOAuthParameter(context, Parameters.PROMPT);

        if (prompt != null) {
            // retrieve prompt values (prompt parameter is a space delimited, case sensitive list of ASCII string values)
            // https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
            List<String> promptValues = Arrays.asList(prompt.split("\\s+"));

            // The Authorization Server MUST NOT display any authentication or consent user interface pages.
            // An error is returned if an End-User is not already authenticated.
            if (promptValues.contains("none") && context.user() == null) {
                throw new LoginRequiredException("Login required");
            }

            // The Authentication Request contains the prompt parameter with the value login.
            // In this case, the Authorization Server MUST reauthenticate the End-User even if the End-User is already authenticated.
            if (promptValues.contains("login") && context.user() != null) {
                if (!returnFromLoginPage(context)) {
                    context.clearUser();
                }
            }
        }
    }

    private void parsePKCEParameter(RoutingContext context, Client client) {
        String codeChallenge = getOAuthParameter(context, io.gravitee.am.common.oauth2.Parameters.CODE_CHALLENGE);
        String codeChallengeMethod = getOAuthParameter(context, io.gravitee.am.common.oauth2.Parameters.CODE_CHALLENGE_METHOD);

        if (codeChallenge == null && codeChallengeMethod != null) {
            throw new InvalidRequestException("Missing parameter: code_challenge");
        }

        final boolean pkceRequiredByFapi = this.domain.usePlainFapiProfile() && Optional.ofNullable((Boolean)context.get(REQUEST_OBJECT_FROM_URI)).orElse(Boolean.FALSE);
        if (codeChallenge == null && (client.isForcePKCE() || pkceRequiredByFapi)) {
            throw new InvalidRequestException("Missing parameter: code_challenge");
        }

        if (codeChallenge != null) {
            if (codeChallengeMethod != null) {
                // https://tools.ietf.org/html/rfc7636#section-4.2
                // It must be plain or S256
                // For FAPI, only S256 is allowed for PKCE
                // https://openid.net/specs/openid-financial-api-part-2-1_0.html#authorization-server (point 18)
                if (((this.domain.usePlainFapiProfile() || client.isForceS256CodeChallengeMethod()) && !CodeChallengeMethod.S256.equalsIgnoreCase(codeChallengeMethod)) ||
                        (!CodeChallengeMethod.S256.equalsIgnoreCase(codeChallengeMethod) &&
                        !CodeChallengeMethod.PLAIN.equalsIgnoreCase(codeChallengeMethod))) {
                    throw new InvalidRequestException("Invalid parameter: code_challenge_method");
                }

            } else {
                // For FAPI, only S256 is allowed for PKCE
                // https://openid.net/specs/openid-financial-api-part-2-1_0.html#authorization-server (point 18)
                // A setting is also available on AM to require the S256 method
                if (this.domain.usePlainFapiProfile() || client.isForceS256CodeChallengeMethod()) {
                    throw new InvalidRequestException("Invalid parameter: code_challenge_method");
                }

                // https://tools.ietf.org/html/rfc7636#section-4.3
                // Default code challenge is plain
                context.request().params().set(io.gravitee.am.common.oauth2.Parameters.CODE_CHALLENGE_METHOD, CodeChallengeMethod.PLAIN);
            }

            // Check that code challenge is valid
            if (!PKCEUtils.validCodeChallenge(codeChallenge)) {
                throw new InvalidRequestException("Invalid parameter: code_challenge");
            }
        }
    }

    protected void parseMaxAgeParameter(RoutingContext context) {
        // if user is already authenticated and if the last login date is greater than the max age parameter,
        // the OP MUST attempt to actively re-authenticate the End-User.
        User authenticatedUser = context.user();
        if (authenticatedUser == null || !(authenticatedUser.getDelegate() instanceof io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User)) {
            // user not authenticated, continue
            return;
        }

        String maxAge = getOAuthParameter(context, Parameters.MAX_AGE);
        if (maxAge == null || !maxAge.matches("-?\\d+")) {
            // none or invalid max age, continue
            return;
        }

        io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) authenticatedUser.getDelegate()).getUser();
        Date loggedAt = endUser.getLoggedAt();
        if (loggedAt == null) {
            // user has no last login date, continue
            return;
        }

        // check the elapsed user session duration
        long elapsedLoginTime = (System.currentTimeMillis() - loggedAt.getTime()) / 1000L;
        Long maxAgeValue = Long.valueOf(maxAge);
        if (maxAgeValue < elapsedLoginTime) {
            // check if the user doesn't come from the login page
            if (!returnFromLoginPage(context)) {
                // should we logout the user or just force it to go to the login page ?
                context.clearUser();

                // check prompt parameter in case the user set 'none' option
                parsePromptParameter(context);
            }
        }
    }

    private void parseClaimsParameter(RoutingContext context) {
        String claims = getOAuthParameter(context, Parameters.CLAIMS);
        OpenIDProviderMetadata openIDProviderMetadata = context.get(PROVIDER_METADATA_CONTEXT_KEY);
        if (claims != null) {
            try {
                ClaimsRequest claimsRequest = claimsRequestResolver.resolve(claims);
                // check acr_values supported
                List<String> acrValuesSupported = openIDProviderMetadata.getAcrValuesSupported();
                if (claimsRequest.getIdTokenClaims() != null
                        && claimsRequest.getIdTokenClaims().get(Claims.acr) != null) {
                    ClaimRequest claimRequest = claimsRequest.getIdTokenClaims().get(Claims.acr);
                    List<String> acrValuesRequested = claimRequest.getValue() != null
                            ? Collections.singletonList(claimRequest.getValue())
                            : claimRequest.getValues() != null ? claimRequest.getValues() : Collections.emptyList();
                    if (!acrValuesSupported.containsAll(acrValuesRequested)) {
                        throw new InvalidRequestException("Invalid parameter: claims, acr_values requested not supported");
                    }
                }
                // save claims request as json string value (will be use for id_token and/or UserInfo endpoint)
                context.request().params().set(Parameters.CLAIMS, Json.encode(claimsRequest));
            } catch (ClaimsRequestSyntaxException e) {
                throw new InvalidRequestException("Invalid parameter: claims");
            }
        }
    }

    private void parseResponseModeParameter(RoutingContext context) {
        String responseMode = getOAuthParameter(context, io.gravitee.am.common.oauth2.Parameters.RESPONSE_MODE);
        OpenIDProviderMetadata openIDProviderMetadata = context.get(PROVIDER_METADATA_CONTEXT_KEY);
        if (responseMode == null) {
            return;
        }

        // get supported response modes
        List<String> responseModesSupported = openIDProviderMetadata.getResponseModesSupported();
        if (!responseModesSupported.contains(responseMode)) {
            throw new UnsupportedResponseModeException("Unsupported response mode: " + responseMode);
        }
    }

    private void parseNonceParameter(RoutingContext context) {
        String nonce = getOAuthParameter(context, io.gravitee.am.common.oidc.Parameters.NONCE);
        String responseType = getOAuthParameter(context, io.gravitee.am.common.oauth2.Parameters.RESPONSE_TYPE);
        // nonce parameter is required for the Hybrid flow
        if (nonce == null && requireNonce(responseType)) {
            throw new InvalidRequestException("Missing parameter: nonce is required for Implicit and Hybrid Flow");
        }
    }

    private void parseGrantTypeParameter(Client client) {
        // Authorization endpoint implies that the client should at least have authorization_code ou implicit grant types.
        List<String> authorizedGrantTypes = client.getAuthorizedGrantTypes();
        if (authorizedGrantTypes == null || authorizedGrantTypes.isEmpty()) {
            throw new UnauthorizedClientException("Client should at least have one authorized grant type");
        }
        if (!containsGrantType(authorizedGrantTypes)) {
            throw new UnauthorizedClientException("Client must at least have authorization_code or implicit grant type enable");
        }
    }

    private void parseResponseTypeParameter(RoutingContext context, Client client) {
        String responseType = getOAuthParameter(context, io.gravitee.am.common.oauth2.Parameters.RESPONSE_TYPE);

        // response_type is required and may be provided by query parameter or by request object
        checkResponseType(responseType, context.get(PROVIDER_METADATA_CONTEXT_KEY));

        // Authorization endpoint implies that the client should have response_type
        if (client.getResponseTypes() == null) {
            throw new UnauthorizedClientException("Client should have response_type.");
        }
        if(!client.getResponseTypes().contains(responseType)) {
            throw new UnauthorizedClientException("Client should have all requested response_type");
        }

        if (this.domain.usePlainFapiProfile()) {
            // For FAPI : https://openid.net/specs/openid-financial-api-part-2-1_0-final.html#authorization-server
            // The authorization server
            //    shall require
            //        the response_type value code id_token, or
            //        the response_type value code in conjunction with the response_mode value jwt;
            String responseMode = getOAuthParameter(context, io.gravitee.am.common.oauth2.Parameters.RESPONSE_MODE);
            if (!((responseType.equals(io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN)) ||
                    (responseType.equals(ResponseType.CODE) && (responseMode != null && responseMode.equalsIgnoreCase("jwt"))))) {
                throw new InvalidRequestException("Invalid response_type");
            }
        }
    }

    private void parseRedirectUriParameter(RoutingContext context, Client client) {
        String requestedRedirectUri = getOAuthParameter(context, io.gravitee.am.common.oauth2.Parameters.REDIRECT_URI);
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

    private boolean returnFromLoginPage(RoutingContext context) {
       return Boolean.TRUE.equals(context.session().get(ConstantKeys.USER_LOGIN_COMPLETED_KEY));
    }

    private boolean containsGrantType(List<String> authorizedGrantTypes) {
        return authorizedGrantTypes.stream()
                .anyMatch(authorizedGrantType -> GrantType.AUTHORIZATION_CODE.equals(authorizedGrantType)
                        || GrantType.IMPLICIT.equals(authorizedGrantType));
    }

    private void checkMatchingRedirectUri(String requestedRedirect, List<String> registeredClientRedirectUris) {
        if (registeredClientRedirectUris
                .stream()
                .noneMatch(registeredClientUri -> redirectMatches(requestedRedirect, registeredClientUri, this.domain.isRedirectUriStrictMatching() || this.domain.usePlainFapiProfile()))) {
            throw new RedirectMismatchException("The redirect_uri MUST match the registered callback URL for this application");
        }
    }
}
