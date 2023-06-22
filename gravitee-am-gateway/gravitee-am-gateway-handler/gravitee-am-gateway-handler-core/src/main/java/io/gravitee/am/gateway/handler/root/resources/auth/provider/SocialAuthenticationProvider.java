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
import io.gravitee.am.common.exception.authentication.LoginCallbackFailedException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.AuthenticationDetails;
import io.gravitee.am.gateway.handler.common.auth.event.AuthenticationEvent;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.UserAuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.common.event.EventManager;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.ACCESS_TOKEN_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ID_TOKEN_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PASSWORD_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PROVIDER_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PROVIDER_ID_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USERNAME_PARAM_KEY;
import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.impl.user.activity.utils.ConsentUtils.canSaveUserAgent;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SocialAuthenticationProvider implements UserAuthProvider {
    private static final Logger logger = LoggerFactory.getLogger(SocialAuthenticationProvider.class);
    private final UserAuthenticationManager userAuthenticationManager;
    private final EventManager eventManager;
    private final IdentityProviderManager identityProviderManager;
    private final Domain domain;

    private GatewayMetricProvider gatewayMetricProvider;

    public SocialAuthenticationProvider(UserAuthenticationManager userAuthenticationManager,
                                        EventManager eventManager,
                                        IdentityProviderManager identityProviderManager,
                                        Domain domain, GatewayMetricProvider gatewayMetricProvider) {
        this.userAuthenticationManager = userAuthenticationManager;
        this.eventManager = eventManager;
        this.identityProviderManager = identityProviderManager;
        this.domain = domain;
        this.gatewayMetricProvider = gatewayMetricProvider;
    }

    @Override
    public void authenticate(RoutingContext context, JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {
        final Client client = context.get(CLIENT_CONTEXT_KEY);
        final AuthenticationProvider authenticationProvider = context.get(PROVIDER_CONTEXT_KEY);
        final String authProvider = context.get(PROVIDER_ID_PARAM_KEY);
        final String username = authInfo.getString(USERNAME_PARAM_KEY);
        final String password = authInfo.getString(PASSWORD_PARAM_KEY);

        logger.debug("Authentication attempt using social identity provider {}", authProvider);

        // create authentication context
        SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(new VertxHttpServerRequest(context.request().getDelegate()));
        authenticationContext.attributes().putAll(context.data());
        authenticationContext.set(Parameters.REDIRECT_URI, authInfo.getString(Parameters.REDIRECT_URI));

        // create user authentication
        EndUserAuthentication endUserAuthentication = new EndUserAuthentication(username, password, authenticationContext);
        if (canSaveIp(context)) {
            endUserAuthentication.getContext().set(Claims.ip_address, RequestUtils.remoteAddress(context.request()));
        }
        if (canSaveUserAgent(context)) {
            endUserAuthentication.getContext().set(Claims.user_agent, RequestUtils.userAgent(context.request()));
        }

        // authenticate the user via the social provider
        authenticationProvider.loadUserByUsername(endUserAuthentication)
                .switchIfEmpty(Single.error(() -> new BadCredentialsException("Unable to authenticate social provider, authentication provider has returned empty value")))
                .flatMap(user -> checkDomainWhitelist(user, authProvider))
                .flatMap(user -> {
                    // set source and client for the current authenticated end-user
                    Map<String, Object> additionalInformation = user.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(user.getAdditionalInformation());
                    additionalInformation.put("source", authProvider);
                    additionalInformation.put(Parameters.CLIENT_ID, client.getClientId());

                    var accessToken = ofNullable(endUserAuthentication.getContext().get(ACCESS_TOKEN_KEY));
                    var idToken = ofNullable(endUserAuthentication.getContext().get(ID_TOKEN_KEY));

                    accessToken.ifPresentOrElse(at -> {
                        // If isStoreOriginalToken, we add both the access_token and id_token in profile since they are present
                        additionalInformation.put(OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY, at);
                        idToken.ifPresent(it -> additionalInformation.put(OIDC_PROVIDER_ID_TOKEN_KEY, it));
                    }, () -> {
                        // We remove both otherwise
                        additionalInformation.remove(OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY);
                        additionalInformation.remove(OIDC_PROVIDER_ID_TOKEN_KEY);
                    });

                    // If id_token is present and SSO is enabled we add the id_token in profile
                    if (client.isSingleSignOut() && idToken.isPresent()) {
                        logger.debug("Single SignOut enable for client '{}' store the id_token coming from the provider {} as additional information", client.getId(), authProvider);
                        additionalInformation.put(OIDC_PROVIDER_ID_TOKEN_KEY, idToken.get());
                    } else if (accessToken.isEmpty()) {
                        // unless isStoreOriginalToken is enabled (e.g access_token isPresent) we can remove id_token from the profile
                        additionalInformation.remove(OIDC_PROVIDER_ID_TOKEN_KEY);
                    }

                    ((DefaultUser) user).setAdditionalInformation(additionalInformation);
                    return userAuthenticationManager.connect(user, client, authenticationContext.request());
                })
                .subscribe(user -> {
                    gatewayMetricProvider.incrementSuccessfulAuth(true);
                    eventManager.publishEvent(AuthenticationEvent.SUCCESS, new AuthenticationDetails(endUserAuthentication, domain, client, user));
                    resultHandler.handle(Future.succeededFuture(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                }, error -> {
                    logger.error("Unable to authenticate social provider", error);
                    gatewayMetricProvider.incrementFailedAuth(true);
                    eventManager.publishEvent(AuthenticationEvent.FAILURE, new AuthenticationDetails(endUserAuthentication, domain, client, error));
                    resultHandler.handle(Future.failedFuture(error));
                });

    }

    private Single<io.gravitee.am.identityprovider.api.User> checkDomainWhitelist(io.gravitee.am.identityprovider.api.User endUser, String identityProviderId) {
        final IdentityProvider identityProvider = this.identityProviderManager.getIdentityProvider(identityProviderId);
        var domainWhitelist = identityProvider.getDomainWhitelist();
        // No whitelist mean we let everyone
        if (nonNull(domainWhitelist) && !domainWhitelist.isEmpty()) {
            // we reject the connection if neither the username nor the email are allowed
            if (!isUsernameWhitelisted(domainWhitelist, endUser.getUsername()) && !isEmailWhitelisted(domainWhitelist, endUser.getEmail())) {
                return Single.error(new LoginCallbackFailedException("could not authenticate user"));
            }
        }
        return Single.just(endUser);
    }

    private static boolean isUsernameWhitelisted(List<String> domainWhitelist, String username) {
        return nonNull(username) && isDomainWhitelisted(username, domainWhitelist);
    }

    private static boolean isEmailWhitelisted(List<String> domainWhitelist, String email) {
        return nonNull(email) && isDomainWhitelisted(email, domainWhitelist);
    }

    private static boolean isDomainWhitelisted(String username, List<String> domainWhitelist) {
        var domainName = username.split("@");
        // username is not an email, fail
        if (domainName.length < 2) {
            logger.debug("Username [{}] is not an email", username);
            return false;
        }

        if (domainWhitelist.stream().noneMatch(domainName[1].trim()::equals)) {
            logger.debug("Username [{}] does not match domainWhitelist", username);
            return false;
        }
        return true;
    }
}
