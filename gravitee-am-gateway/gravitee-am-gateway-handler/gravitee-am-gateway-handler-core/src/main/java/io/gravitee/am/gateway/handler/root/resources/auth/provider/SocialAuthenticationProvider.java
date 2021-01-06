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
package io.gravitee.am.gateway.handler.root.resources.auth.provider;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.AuthenticationDetails;
import io.gravitee.am.gateway.handler.common.auth.event.AuthenticationEvent;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.UserAuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.event.EventManager;
import io.reactivex.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SocialAuthenticationProvider implements UserAuthProvider {

    private final Logger logger = LoggerFactory.getLogger(SocialAuthenticationProvider.class);

    private UserAuthenticationManager userAuthenticationManager;

    private EventManager eventManager;

    private Domain domain;

    public SocialAuthenticationProvider() {
    }

    public SocialAuthenticationProvider(UserAuthenticationManager userAuthenticationManager, EventManager eventManager, Domain domain) {
        this.userAuthenticationManager = userAuthenticationManager;
        this.eventManager = eventManager;
        this.domain = domain;
    }

    @Override
    public void authenticate(RoutingContext context, JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final AuthenticationProvider authenticationProvider = context.get(ConstantKeys.PROVIDER_CONTEXT_KEY);
        final String authProvider = context.get(ConstantKeys.PROVIDER_ID_PARAM_KEY);
        final String username = authInfo.getString(ConstantKeys.USERNAME_PARAM_KEY);
        final String password = authInfo.getString(ConstantKeys.PASSWORD_PARAM_KEY);

        logger.debug("Authentication attempt using social identity provider {}", authProvider);

        // create authentication context
        SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(new VertxHttpServerRequest(context.request().getDelegate()));
        authenticationContext.attributes().putAll(context.data());
        authenticationContext.set(Parameters.REDIRECT_URI, authInfo.getString(Parameters.REDIRECT_URI));

        // create user authentication
        EndUserAuthentication endUserAuthentication = new EndUserAuthentication(username, password, authenticationContext);
        endUserAuthentication.getContext().set(Claims.ip_address, RequestUtils.remoteAddress(context.request()));
        endUserAuthentication.getContext().set(Claims.user_agent, RequestUtils.userAgent(context.request()));

        // authenticate the user via the social provider
        authenticationProvider.loadUserByUsername(endUserAuthentication)
                .switchIfEmpty(Maybe.error(new BadCredentialsException("Unable to authenticate social provider, authentication provider has returned empty value")))
                .flatMapSingle(user -> {
                    // set source and client for the current authenticated end-user
                    Map<String, Object> additionalInformation = user.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(user.getAdditionalInformation());
                    additionalInformation.put("source", authProvider);
                    additionalInformation.put(Parameters.CLIENT_ID, client.getClientId());
                    ((DefaultUser) user).setAdditionalInformation(additionalInformation);
                    return userAuthenticationManager.connect(user);
                })
                .subscribe(user -> {
                    eventManager.publishEvent(AuthenticationEvent.SUCCESS, new AuthenticationDetails(endUserAuthentication, domain, client, user));
                    resultHandler.handle(Future.succeededFuture(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                }, error -> {
                    logger.error("Unable to authenticate social provider", error);
                    eventManager.publishEvent(AuthenticationEvent.FAILURE, new AuthenticationDetails(endUserAuthentication, domain, client, error));
                    resultHandler.handle(Future.failedFuture(error));
                });

    }
}
