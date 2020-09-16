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
package io.gravitee.am.gateway.handler.common.auth.user.impl;

import io.gravitee.am.common.exception.authentication.*;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.AuthenticationDetails;
import io.gravitee.am.gateway.handler.common.auth.event.AuthenticationEvent;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.User;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.search.LoginAttemptCriteria;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.common.event.EventManager;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuthenticationManagerImpl implements UserAuthenticationManager {

    private final Logger logger = LoggerFactory.getLogger(UserAuthenticationManagerImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private UserAuthenticationService userAuthenticationService;

    @Override
    public Single<User> authenticate(Client client, Authentication authentication) {
        logger.debug("Trying to authenticate [{}]", authentication);

        // Get identity providers associated to a client
        // For each idp, try to authenticate a user
        // Try to authenticate while the user can not be authenticated
        // If user can't be authenticated, send an exception

        // Skip external identity provider for authentication with credentials.
        List<String> identities = client.getIdentities() != null ?
                client.getIdentities()
                        .stream()
                        .map(idp -> identityProviderManager.getIdentityProvider(idp))
                        .filter(idp -> idp != null && !idp.isExternal())
                        .map(IdentityProvider::getId)
                        .collect(Collectors.toList()) : null;
        if (identities == null || identities.isEmpty()) {
            logger.error("No identity provider found for client : " + client.getClientId());
            return Single.error(new InternalAuthenticationServiceException("No identity provider found for client : " + client.getClientId()));
        }

        return Observable.fromIterable(identities)
                .flatMapMaybe(authProvider -> authenticate0(client, authentication, authProvider))
                .takeUntil(userAuthentication -> userAuthentication.getUser() != null || userAuthentication.getLastException() instanceof AccountLockedException)
                .lastOrError()
                .flatMap(userAuthentication -> {
                    io.gravitee.am.identityprovider.api.User user = userAuthentication.getUser();
                    if (user == null) {
                        Throwable lastException = userAuthentication.getLastException();
                        if (lastException != null) {
                            if (lastException instanceof BadCredentialsException) {
                                return Single.error(new BadCredentialsException("The credentials you entered are invalid", lastException));
                            } else if (lastException instanceof UsernameNotFoundException) {
                                return Single.error(new UsernameNotFoundException("Invalid or unknown user"));
                            } else if (lastException instanceof AccountStatusException) {
                                return Single.error(lastException);
                            } else {
                                logger.error("An error occurs during user authentication", lastException);
                                return Single.error(new InternalAuthenticationServiceException("Unable to validate credentials. The user account you are trying to access may be experiencing a problem.", lastException));
                            }
                        } else {
                            return Single.error(new UsernameNotFoundException("No user found for registered providers"));
                        }
                    } else {
                        // complete user connection
                        return connect(user);
                    }
                })
                .doOnSuccess(user -> eventManager.publishEvent(AuthenticationEvent.SUCCESS, new AuthenticationDetails(authentication, domain, client, user)))
                .doOnError(throwable -> eventManager.publishEvent(AuthenticationEvent.FAILURE, new AuthenticationDetails(authentication, domain, client, throwable)));
    }

    @Override
    public Maybe<User> loadUserByUsername(String subject) {
        return userAuthenticationService.loadPreAuthenticatedUser(subject);
    }

    @Override
    public Single<User> connect(io.gravitee.am.identityprovider.api.User user, boolean afterAuthentication) {
        return userAuthenticationService.connect(user, afterAuthentication);
    }

    private Maybe<UserAuthentication> authenticate0(Client client, Authentication authentication, String authProvider) {
        return preAuthentication(client, authentication, authProvider)
                .andThen(identityProviderManager.get(authProvider))
                .switchIfEmpty(Maybe.error(new BadCredentialsException("Unable to load authentication provider " + authProvider + ", an error occurred during the initialization stage")))
                .flatMap(authenticationProvider -> {
                    logger.debug("Authentication attempt using identity provider {} ({})", authenticationProvider, authenticationProvider.getClass().getName());
                    return authenticationProvider.loadUserByUsername(authentication)
                            .switchIfEmpty(Maybe.error(new UsernameNotFoundException((String) authentication.getPrincipal())));
                })
                .map(user -> {
                    logger.debug("Successfully Authenticated: " + authentication.getPrincipal() + " with provider authentication provider " + authProvider);
                    Map<String, Object> additionalInformation = user.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(user.getAdditionalInformation());
                    additionalInformation.put("source", authProvider);
                    additionalInformation.put(Parameters.CLIENT_ID, client.getId());
                    ((DefaultUser ) user).setAdditionalInformation(additionalInformation);
                    return new UserAuthentication(user, null);
                })
                .onErrorResumeNext(error -> {
                    logger.debug("Unable to authenticate [{}] with authentication provider [{}]", authentication.getPrincipal(), authProvider, error);
                    return Maybe.just(new UserAuthentication(null, error));
                })
                .flatMap(userAuthentication -> postAuthentication(client, authentication, authProvider, userAuthentication).andThen(Maybe.just(userAuthentication)));
    }

    private Completable preAuthentication(Client client, Authentication authentication, String source) {
        final AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
        if (accountSettings != null && accountSettings.isLoginAttemptsDetectionEnabled()) {
            LoginAttemptCriteria criteria = new LoginAttemptCriteria.Builder()
                    .domain(domain.getId())
                    .client(client.getId())
                    .identityProvider(source)
                    .username((String) authentication.getPrincipal())
                    .build();
            return loginAttemptService
                    .checkAccount(criteria, accountSettings)
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty())
                    .flatMapCompletable(optLoginAttempt -> {
                        if (optLoginAttempt.isPresent() && optLoginAttempt.get().isAccountLocked(accountSettings.getMaxLoginAttempts())) {
                            Map<String, String> details = new HashMap<>();
                            details.put("attempt_id", optLoginAttempt.get().getId());
                            return Completable.error(new AccountLockedException("User " + authentication.getPrincipal() + " is locked", details));
                        }
                        return Completable.complete();
                    });
        }
        return Completable.complete();
    }

    private Completable postAuthentication(Client client, Authentication authentication, String source, UserAuthentication userAuthentication) {
        final AccountSettings accountSettings = AccountSettings.getInstance(domain, client);
        if (accountSettings != null && accountSettings.isLoginAttemptsDetectionEnabled()) {
            LoginAttemptCriteria criteria = new LoginAttemptCriteria.Builder()
                    .domain(domain.getId())
                    .client(client.getId())
                    .identityProvider(source)
                    .username((String) authentication.getPrincipal())
                    .build();
            // no exception clear login attempt
            if (userAuthentication.getLastException() == null) {
                return loginAttemptService.loginSucceeded(criteria);
            } else if (userAuthentication.getLastException() instanceof BadCredentialsException){
                return loginAttemptService.loginFailed(criteria, accountSettings);
            }
        }
        return Completable.complete();
    }

    private class UserAuthentication {
        private io.gravitee.am.identityprovider.api.User user;
        private Throwable lastException;

        public UserAuthentication() {
        }

        public UserAuthentication(io.gravitee.am.identityprovider.api.User user, Throwable lastException) {
            this.user = user;
            this.lastException = lastException;
        }

        public io.gravitee.am.identityprovider.api.User getUser() {
            return user;
        }

        public Throwable getLastException() {
            return lastException;
        }
    }
}
