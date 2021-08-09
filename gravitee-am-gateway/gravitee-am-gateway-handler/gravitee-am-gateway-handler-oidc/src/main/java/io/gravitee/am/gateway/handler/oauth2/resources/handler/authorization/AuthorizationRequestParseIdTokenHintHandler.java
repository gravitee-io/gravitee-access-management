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
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oauth2.exception.LoginRequiredException;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.ParamUtils.getOAuthParameter;

/**
 * Silent Re-authentication of subject with ID Token.
 *
 * ID Token previously issued by the Authorization Server being passed as a hint about the End-User's current or past authenticated session with the Client.
 *
 * If the End-User identified by the ID Token is logged in or is logged in by the request, then the Authorization Server returns a positive response; otherwise, it SHOULD return an error, such as login_required.
 *
 * When possible, an id_token_hint SHOULD be present when prompt=none is used and an invalid_request error MAY be returned if it is not; however, the server SHOULD respond successfully when possible, even if it is not present.
 *
 * The Authorization Server need not be listed as an audience of the ID Token when it is used as an id_token_hint value.
 *
 * If the ID Token received by the RP from the OP is encrypted, to use it as an id_token_hint, the Client MUST decrypt the signed ID Token contained within the encrypted ID Token.
 * The Client MAY re-encrypt the signed ID token to the Authentication Server using a key that enables the server to decrypt the ID Token, and use the re-encrypted ID token as the id_token_hint value.
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.  Authentication Request</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestParseIdTokenHintHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationRequestParseIdTokenHintHandler.class);
    private final IDTokenService idTokenService;

    public AuthorizationRequestParseIdTokenHintHandler(IDTokenService idTokenService) {
        this.idTokenService = idTokenService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final String idTokenHint = getOAuthParameter(routingContext, Parameters.ID_TOKEN_HINT);
        final String prompt = getOAuthParameter(routingContext, Parameters.PROMPT);

        // if no id_token_hint parameter, continue;
        if (idTokenHint == null) {
            routingContext.next();
            return;
        }

        // id_token_hint parameter should be present when prompt=none is used, if not respond with invalid_request exception
        if (prompt == null) {
            throw new InvalidRequestException("prompt parameter must be present when id_token_hint is used");
        }

        // retrieve prompt values (prompt parameter is a space delimited, case sensitive list of ASCII string values)
        // https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
        List<String> promptValues = Arrays.asList(prompt.split("\\s+"));
        if (!promptValues.contains("none")) {
            throw new InvalidRequestException("prompt=none must be present when id_token_hint is used");
        }

        // if client has not enabled this option, continue
        if (!client.isSilentReAuthentication()) {
            routingContext.next();
            return;
        }

        // process silent re-authentication
        extractUser(idTokenHint, client, h -> {
            if (h.failed()) {
                // if no user, continue
                logger.debug("An error has occurred when extracting user from the ID token", h.cause());
                routingContext.next();
                return;
            }

            // set user in context and continue
            io.gravitee.am.model.User endUser = h.result();
            if (routingContext.user() == null) {
                routingContext.setUser(User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser)));
                routingContext.next();
                return;
            }

            // if current user is not the same as the id token one, return login_required error
            io.gravitee.am.model.User loggedInUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            if (!loggedInUser.getId().equals(endUser.getId())) {
                logger.debug("The End-User identified by the ID Token is not the same as the logged in End-User.");
                routingContext.fail(new LoginRequiredException("Login required"));
            } else {
                routingContext.next();
            }
        });
    }

    private void extractUser(String idToken, Client client, Handler<AsyncResult<io.gravitee.am.model.User>> handler) {
        idTokenService.extractUser(idToken, client)
                .subscribe(
                        user -> handler.handle(Future.succeededFuture(user)),
                        error -> handler.handle(Future.failedFuture(error))
                );
    }
}
