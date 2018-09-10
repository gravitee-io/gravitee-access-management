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
package io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.LoginRequiredException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedResponseTypeException;
import io.gravitee.am.gateway.handler.oauth2.pkce.PKCEUtils;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.oauth2.utils.OIDCParameters;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.request.AuthorizationRequestFactory;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;

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
public class AuthorizationRequestParseHandler implements Handler<RoutingContext> {

    private static final String RETURN_FROM_LOGIN_PAGE = "return_from_login_page";
    private final AuthorizationRequestFactory authorizationRequestFactory = new AuthorizationRequestFactory();

    @Override
    public void handle(RoutingContext context) {
        AuthorizationRequest authorizationRequest = authorizationRequestFactory.create(context.request());

        // The authorization server validates the request to ensure that all required parameters are present and valid.
        String responseType = authorizationRequest.getResponseType();
        String clientId = authorizationRequest.getClientId();

        if (responseType == null || (!responseType.equals(OAuth2Constants.TOKEN) && !responseType.equals(OAuth2Constants.CODE))) {
            throw new UnsupportedResponseTypeException("Unsupported response type: " + responseType);
        }

        if (clientId == null) {
            throw new InvalidRequestException("A client id is required");
        }

        // proceed prompt parameter
        parsePromptParameter(context);

        // proceed pkce parameter
        parsePKCEParameter(context);

        // proceed max_age parameter
        parseMaxAgeParameter(context);

        context.next();
    }

    private void parsePromptParameter(RoutingContext context) {
        String prompt = context.request().getParam(OIDCParameters.PROMPT);

        if (prompt != null) {
            // retrieve prompt values (prompt parameter is a space delimited, case sensitive list of ASCII string values)
            // https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
            List<String> promptValues = Arrays.asList(prompt.split("\\s+"));

            // The Authorization Server MUST NOT display any authentication or consent user interface pages.
            // An error is returned if an End-User is not already authenticated.
            if (promptValues.contains("none") && context.user() == null) {
                throw new LoginRequiredException();
            }
        }
    }

    private void parsePKCEParameter(RoutingContext context) {
        String codeChallenge = context.request().getParam(OAuth2Constants.CODE_CHALLENGE);
        String codeChallengeMethod = context.request().getParam(OAuth2Constants.CODE_CHALLENGE_METHOD);

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
            if (!OAuth2Constants.PKCE_METHOD_S256.equalsIgnoreCase(codeChallengeMethod) &&
                    !OAuth2Constants.PKCE_METHOD_PLAIN.equalsIgnoreCase(codeChallengeMethod)) {
                throw new InvalidRequestException("Invalid parameter: code_challenge_method");
            }
        } else {
            // https://tools.ietf.org/html/rfc7636#section-4.3
            // Default code challenge is plain
            context.request().params().set(OAuth2Constants.CODE_CHALLENGE_METHOD, OAuth2Constants.PKCE_METHOD_PLAIN);
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
        if (authenticatedUser == null || !(authenticatedUser.getDelegate() instanceof io.gravitee.am.gateway.handler.vertx.auth.user.User)) {
            // user not authenticated, continue
            return;
        }

        String maxAge = context.request().getParam(OIDCParameters.MAX_AGE);
        if (maxAge == null || !maxAge.matches("-?\\d+")) {
            // none or invalid max age, continue
            return;
        }

        io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.vertx.auth.user.User) authenticatedUser.getDelegate()).getUser();
        Date loggedAt = endUser.getLoggedAt();
        if (loggedAt == null) {
            // user has no last login date, continue
            return;
        }

        // check the elapsed user session duration
        if (context.session() != null) {
            long elapsedLoginTime = (System.currentTimeMillis() - loggedAt.getTime()) / 1000L;
            Long maxAgeValue = Long.valueOf(maxAge);
            if (maxAgeValue < elapsedLoginTime) {
                // check if the user doesn't come from the login page
                Boolean returnFromLoginPage = context.session().get(RETURN_FROM_LOGIN_PAGE);
                if (returnFromLoginPage == null || !returnFromLoginPage) {
                    // should we logout the user or just force it to go to the login page ?
                    context.clearUser();
                    // check prompt parameter in case the user set 'none' option
                    parsePromptParameter(context);
                }
            }
            // clean up session
            context.session().remove(RETURN_FROM_LOGIN_PAGE);
        }
    }

    public static AuthorizationRequestParseHandler create() {
        return new AuthorizationRequestParseHandler();
    }
}
