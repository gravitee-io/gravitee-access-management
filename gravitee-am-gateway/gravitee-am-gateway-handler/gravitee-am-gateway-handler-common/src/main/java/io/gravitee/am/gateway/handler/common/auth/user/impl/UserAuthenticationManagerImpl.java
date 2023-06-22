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

import com.google.common.base.Strings;
import io.gravitee.am.common.exception.authentication.AccountLockedException;
import io.gravitee.am.common.exception.authentication.AccountPasswordExpiredException;
import io.gravitee.am.common.exception.authentication.AccountStatusException;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.authentication.InternalAuthenticationServiceException;
import io.gravitee.am.common.exception.authentication.NegotiateContinueException;
import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.AuthenticationDetails;
import io.gravitee.am.gateway.handler.common.auth.event.AuthenticationEvent;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationService;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.repository.management.api.search.LoginAttemptCriteria;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.common.event.EventManager;
import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.gravitee.am.identityprovider.api.AuthenticationProvider.ACTUAL_USERNAME;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

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

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private GatewayMetricProvider gatewayMetricProvider;

    @Override
    public Single<User> authenticate(Client client, Authentication authentication, boolean preAuthenticated) {
        logger.debug("Trying to authenticate [{}]", authentication);

        var applicationIdentityProviders = getApplicationIdentityProviders(client);
        if (isNull(applicationIdentityProviders) || applicationIdentityProviders.isEmpty()) {
            return Single.error(() -> getInternalAuthenticationServiceException(client));
        }

        return Observable.fromIterable(applicationIdentityProviders)
                .filter(appIdp -> selectionRuleMatches(appIdp.getSelectionRule(), authentication.copy()))
                .switchIfEmpty(Observable.error(() -> getInternalAuthenticationServiceException(client)))
                .concatMapMaybe(appIdp -> authenticate0(client, authentication.copy(), appIdp.getIdentity(), preAuthenticated))
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
                                // if an IdP return UsernameNotFoundException, convert it as BadCredentials in order to avoid helping attackers
                                return Single.error(new BadCredentialsException("The credentials you entered are invalid", lastException));
                            } else if (lastException instanceof AccountStatusException) {
                                return Single.error(lastException);
                            } else if (lastException instanceof NegotiateContinueException) {
                                return Single.error(lastException);
                            } else {
                                logger.error("An error occurs during user authentication", lastException);
                                return Single.error(new InternalAuthenticationServiceException("Unable to validate credentials. The user account you are trying to access may be experiencing a problem.", lastException));
                            }
                        } else {
                            // if an IdP return null user, throw BadCredentials in order to avoid helping attackers
                            return Single.error(new BadCredentialsException("The credentials you entered are invalid"));
                        }
                    } else {
                        // complete user connection
                        return connect(user, client, authentication.getContext().request())
                                .flatMap(connectedUser -> checkAccountPasswordExpiry(client, connectedUser));
                    }
                })
                .doOnSuccess(user -> {
                    gatewayMetricProvider.incrementSuccessfulAuth(false);
                    eventManager.publishEvent(AuthenticationEvent.SUCCESS, new AuthenticationDetails(authentication, domain, client, user));
                })
                .doOnError(throwable -> {
                    gatewayMetricProvider.incrementFailedAuth(false);
                    eventManager.publishEvent(AuthenticationEvent.FAILURE, new AuthenticationDetails(authentication, domain, client, throwable));
                });
    }

    @Override
    public Maybe<User> loadPreAuthenticatedUser(String subject, Request request) {
        return userAuthenticationService.loadPreAuthenticatedUser(subject, request);
    }

    @Override
    public Single<User> connect(io.gravitee.am.identityprovider.api.User user, Client client, Request request, boolean afterAuthentication) {
        return userAuthenticationService.connect(user, client, request, afterAuthentication);
    }

    @Override
    public Single<User> connectWithPasswordless(Client client, String subject, Authentication authentication) {
        return userAuthenticationService.connectWithPasswordless(subject, client)
                .doOnSuccess(user -> eventManager.publishEvent(AuthenticationEvent.SUCCESS, new AuthenticationDetails(authentication, domain, client, user)))
                .doOnError(throwable -> eventManager.publishEvent(AuthenticationEvent.FAILURE, new AuthenticationDetails(authentication, domain, client, throwable)));

    }

    private Maybe<UserAuthentication> authenticate0(Client client, Authentication authentication, String authProvider, boolean preAuthenticated) {
        return loadUserByUsername0(client, authentication, authProvider, preAuthenticated)
                .flatMap(userAuthentication ->
                        postAuthentication(client, authentication, authProvider, userAuthentication)
                                .andThen(Maybe.just(userAuthentication)));
    }

    private Maybe<UserAuthentication> loadUserByUsername0(Client client, Authentication authentication, String authProvider, boolean preAuthenticated) {
        return identityProviderManager.get(authProvider)
                .switchIfEmpty(Maybe.error(() -> new BadCredentialsException("Unable to load authentication provider " + authProvider + ", an error occurred during the initialization stage")))
                .flatMap(authenticationProvider -> {
                    logger.debug("Authentication attempt using identity provider {} ({})", authenticationProvider, authenticationProvider.getClass().getName());
                    return Maybe.just(preAuthenticated)
                            .flatMap(preAuth -> {
                                if (preAuth) {
                                    final String username = authentication.getPrincipal().toString();
                                    return userService.findByDomainAndUsernameAndSource(domain.getId(), username, authProvider)
                                            .switchIfEmpty(Maybe.error(() -> new UsernameNotFoundException(username)))
                                            .flatMap(user -> {
                                                final Authentication enhanceAuthentication = new EndUserAuthentication(user, null, authentication.getContext());
                                                return authenticationProvider.loadPreAuthenticatedUser(enhanceAuthentication);
                                            });
                                } else {
                                    return authenticationProvider.loadUserByUsername(authentication);
                                }
                            })
                            .switchIfEmpty(Maybe.error(() -> new UsernameNotFoundException(authentication.getPrincipal().toString())));
                })
                .map(user -> {
                    logger.debug("Successfully Authenticated: " + authentication.getPrincipal() + " with provider authentication provider " + authProvider);
                    Map<String, Object> additionalInformation = user.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(user.getAdditionalInformation());
                    additionalInformation.put("source", authProvider);
                    additionalInformation.put(Parameters.CLIENT_ID, client.getId());
                    ((DefaultUser) user).setAdditionalInformation(additionalInformation);
                    return new UserAuthentication(user, null);
                })
                .onErrorResumeNext(error -> {
                    logger.debug("Unable to authenticate [{}] with authentication provider [{}]", authentication.getPrincipal(), authProvider, error);
                    return Maybe.just(new UserAuthentication(null, error));
                });
    }

    private Completable postAuthentication(Client client, Authentication authentication, String source, UserAuthentication userAuthentication) {
       /*
         We do not primarily rely on authentication.getPrincipal() here. The reason is that some identity providers
         (today JDBC and MongoDB) allow you to login while checking multiple input sources (username or email).
         If you input your email, AM will check on its own username field which might not be the email, ending in not
         finding the user and not incrementing the login attempts.
       */
        String username = ofNullable(authentication.getContext())
                .map(ctx -> (String) ctx.get(ACTUAL_USERNAME))
                .orElse(authentication.getPrincipal().toString());
        return postAuthentication(client, username, source, userAuthentication);
    }

    private Completable postAuthentication(Client client, String username, String source, UserAuthentication userAuthentication) {
        final AccountSettings accountSettings = AccountSettings.getInstance(domain, client);

        // if brute force detection feature disabled, continue
        if (accountSettings == null || !accountSettings.isLoginAttemptsDetectionEnabled()) {
            return Completable.complete();
        }

        final LoginAttemptCriteria criteria = new LoginAttemptCriteria.Builder()
                .domain(domain.getId())
                .client(client.getId())
                .identityProvider(source)
                .username(username)
                .build();

        // check if user is locked
        return loginAttemptService
                .checkAccount(criteria, accountSettings)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMapCompletable(optLoginAttempt -> {
                    if (optLoginAttempt.isPresent() && optLoginAttempt.get().isAccountLocked(accountSettings.getMaxLoginAttempts())) {
                        Map<String, String> details = new HashMap<>();
                        details.put("attempt_id", optLoginAttempt.get().getId());
                        return Completable.error(new AccountLockedException("User " + username + " is locked", details));
                    }

                    // no exception clear login attempt
                    if (userAuthentication.getLastException() == null) {
                        return loginAttemptService.loginSucceeded(criteria);
                    }

                    if (userAuthentication.getLastException() instanceof BadCredentialsException) {
                        // do not execute login attempt feature for non existing users
                        // normally the IdP should respond with Maybe.empty() or UsernameNotFoundException
                        // but we can't control custom IdP that's why we have to check user existence
                        return userService.findByDomainAndUsernameAndSource(criteria.domain(), criteria.username(), criteria.identityProvider())
                                .flatMapCompletable(user -> loginAttemptService.loginFailed(criteria, accountSettings)
                                        .flatMapCompletable(loginAttempt -> {
                                            if (loginAttempt.isAccountLocked(accountSettings.getMaxLoginAttempts())) {
                                                return userAuthenticationService.lockAccount(criteria, accountSettings, client, user);
                                            }
                                            return Completable.complete();
                                        })
                                );
                    }
                    return Completable.complete();
                });
    }

    private Single<User> checkAccountPasswordExpiry(Client client, User connectedUser) {
        if (passwordService.checkAccountPasswordExpiry(connectedUser, client, domain)) {
            return Single.error(new AccountPasswordExpiredException("Account's password is expired "));
        }
        return Single.just(connectedUser);
    }

    private List<ApplicationIdentityProvider> getApplicationIdentityProviders(Client client) {
        // Get identity providers associated to a client
        // For each idp, try to authenticate a user
        // Try to authenticate while the user can not be authenticated
        // If user can't be authenticated, send an exception

        // Skip external identity provider for authentication with credentials.
        if (isNull(client.getIdentityProviders())) {
            return List.of();
        }
        return client.getIdentityProviders().stream().filter(appIdp -> {
                    var identityProvider = identityProviderManager.getIdentityProvider(appIdp.getIdentity());
                    return nonNull(identityProvider) && !identityProvider.isExternal();
                })
                .collect(toList());
    }

    private boolean selectionRuleMatches(String rule, Authentication authentication) {
        try {
            // We keep the idp if the rule is not present to keep the same behaviour
            // The priority will define the order of the identity providers to match the rule first
            if (Strings.isNullOrEmpty(rule) || rule.isBlank()) {
                return true;
            }
            var templateEngine = authentication.getContext().getTemplateEngine();
            return templateEngine != null && templateEngine.getValue(rule.trim(), Boolean.class);
        } catch (Exception e) {
            logger.warn("Cannot evaluate the expression [{}] as boolean", rule);
            return false;
        }
    }

    private InternalAuthenticationServiceException getInternalAuthenticationServiceException(Client client) {
        return new InternalAuthenticationServiceException("No identity provider found for client : " + client.getClientId());
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
