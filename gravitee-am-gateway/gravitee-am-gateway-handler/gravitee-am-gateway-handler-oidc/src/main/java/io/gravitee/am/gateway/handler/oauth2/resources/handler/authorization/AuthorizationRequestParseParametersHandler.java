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
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.exception.LoginRequiredException;
import io.gravitee.am.gateway.handler.oauth2.service.pkce.PKCEUtils;
import io.gravitee.am.gateway.handler.oidc.exception.ClaimsRequestSyntaxException;
import io.gravitee.am.gateway.handler.oidc.service.request.ClaimsRequest;
import io.gravitee.am.gateway.handler.oidc.service.request.ClaimsRequestResolver;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * The authorization server validates the request to ensure that all required parameters are present and valid.
 * If the request is valid, the authorization server authenticates the resource owner and obtains
 * an authorization decision (by asking the resource owner or by establishing approval via other means).
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.1.1">4.1.1. Authorization Request</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestParseParametersHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationRequestParseParametersHandler.class);
    private final ClaimsRequestResolver claimsRequestResolver = new ClaimsRequestResolver();
    private Domain domain;


    public AuthorizationRequestParseParametersHandler(Domain domain) {
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        // parse scope parameter
        parseScopeParameter(context);

        // proceed prompt parameter
        parsePromptParameter(context);

        // proceed pkce parameter
        parsePKCEParameter(context);

        // proceed max_age parameter
        parseMaxAgeParameter(context);

        // proceed claims parameter
        parseClaimsParameter(context);

        context.next();
    }

    private void parseScopeParameter(RoutingContext context) {
        // Check scope parameter
        String scopes = context.request().params().get(io.gravitee.am.common.oauth2.Parameters.SCOPE);
        if (scopes != null && scopes.isEmpty()) {
            throw new InvalidScopeException("Invalid parameter: scope must not be empty");
        }
    }

    private void parsePromptParameter(RoutingContext context) {
        String prompt = context.request().getParam(Parameters.PROMPT);

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

    private void parsePKCEParameter(RoutingContext context) {
        String codeChallenge = context.request().getParam(io.gravitee.am.common.oauth2.Parameters.CODE_CHALLENGE);
        String codeChallengeMethod = context.request().getParam(io.gravitee.am.common.oauth2.Parameters.CODE_CHALLENGE_METHOD);

        if (codeChallenge == null && codeChallengeMethod != null) {
            throw new InvalidRequestException("Missing parameter: code_challenge");
        }

        if (codeChallenge == null) {
            // No code challenge provided by client
            return;
        }

        if (codeChallengeMethod != null) {
            // https://tools.ietf.org/html/rfc7636#section-4.2
            // It must be plain or S256
            if (!CodeChallengeMethod.S256.equalsIgnoreCase(codeChallengeMethod) &&
                    !CodeChallengeMethod.PLAIN.equalsIgnoreCase(codeChallengeMethod)) {
                throw new InvalidRequestException("Invalid parameter: code_challenge_method");
            }
        } else {
            // https://tools.ietf.org/html/rfc7636#section-4.3
            // Default code challenge is plain
            context.request().params().set(io.gravitee.am.common.oauth2.Parameters.CODE_CHALLENGE_METHOD, CodeChallengeMethod.PLAIN);
        }

        // Check that code challenge is valid
        if (!PKCEUtils.validCodeChallenge(codeChallenge)) {
            throw new InvalidRequestException("Invalid parameter: code_challenge");
        }
    }

    private void parseMaxAgeParameter(RoutingContext context) {
        // if user is already authenticated and if the last login date is greater than the max age parameter,
        // the OP MUST attempt to actively re-authenticate the End-User.
        User authenticatedUser = context.user();
        if (authenticatedUser == null || !(authenticatedUser.getDelegate() instanceof io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User)) {
            // user not authenticated, continue
            return;
        }

        String maxAge = context.request().getParam(Parameters.MAX_AGE);
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
        String claims = context.request().getParam(Parameters.CLAIMS);
        if (claims != null) {
            try {
                ClaimsRequest claimsRequest = claimsRequestResolver.resolve(claims);
                // save claims request as json string value (will be use for id_token and/or UserInfo endpoint)
                context.request().params().set(Parameters.CLAIMS, Json.encode(claimsRequest));
            } catch (ClaimsRequestSyntaxException e) {
                throw new InvalidRequestException("Invalid parameter: claims");
            }
        }
    }

    private boolean returnFromLoginPage(RoutingContext context) {
        String referer = context.request().headers().get(HttpHeaders.REFERER);
        try {
            return referer != null && UriBuilder.fromURIString(referer).build().getPath().contains('/' + domain.getPath() + "/login");
        } catch (URISyntaxException e) {
            logger.debug("Unable to calculate referer url : {}", referer, e);
            return false;
        }
    }
}
