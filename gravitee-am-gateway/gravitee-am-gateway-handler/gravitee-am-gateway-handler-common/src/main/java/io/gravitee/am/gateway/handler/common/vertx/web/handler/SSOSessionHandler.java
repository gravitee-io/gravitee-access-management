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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.gravitee.am.common.exception.authentication.AccountDisabledException;
import io.gravitee.am.common.exception.authentication.AccountIllegalStateException;
import io.gravitee.am.common.exception.authentication.AccountLockedException;
import io.gravitee.am.common.exception.authentication.AccountStatusException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.dataplane.api.search.LoginAttemptCriteria;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieSession;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserIdentity;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.Client;

import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.LoginAttemptService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.rxjava3.ext.auth.User;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

/**
 * SSO Session Handler to check if the user stored in the HTTP session is still "valid" upon the incoming request
 *
 * - a user is invalid if he is disabled
 * - a user is invalid if he is not on a shared identity provider
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SSOSessionHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSOSessionHandler.class);
    private ClientSyncService clientSyncService;
    private AuthenticationFlowContextService authenticationFlowContextService;
    private LoginAttemptService loginAttemptService;
    private Domain domain;

    public SSOSessionHandler(ClientSyncService clientSyncService, AuthenticationFlowContextService authenticationFlowContextService, LoginAttemptService loginAttemptService, Domain domain) {
        this.clientSyncService = clientSyncService;
        this.authenticationFlowContextService = authenticationFlowContextService;
        this.loginAttemptService = loginAttemptService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        // if no user in context, continue
        if (context.user() == null) {
            context.next();
            return;
        }

        authorizeUser(context, h -> {
            if (h.failed()) {
                Throwable cause = h.cause();
                LOGGER.debug("An error occurs while checking SSO Session upon the current user : {}", context.user().principal(), cause);
                if (cause instanceof AccountStatusException) {
                    // user has been disabled, invalidate session

                    // clear AuthenticationFlowContext. data of this context have a TTL so we can fire and forget in case on error.
                    authenticationFlowContextService.clearContext(context.session().get(ConstantKeys.TRANSACTION_ID_KEY))
                            .doOnError((error) -> LOGGER.info("Deletion of some authentication flow data fails '{}'", error.getMessage()))
                            .subscribe();

                    context.clearUser();
                    context.session().destroy();
                } else if (cause instanceof InvalidRequestException) {
                    context.fail(new HttpException(403, "Invalid request for the current SSO context"));
                    return;
                }
            }
            context.next();
        });

    }

    private void authorizeUser(RoutingContext context, Handler<AsyncResult<Void>> handler) {
        // retrieve end user and check if it's authorized to call the subsequence handlers
        User authenticatedUser = context.user();
        io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) authenticatedUser.getDelegate()).getUser();

        // check account status
        checkAccountStatus(context, endUser, accountHandler -> {
            if (accountHandler.failed()) {
                handler.handle(Future.failedFuture(accountHandler.cause()));
                return;
            }
            // additional check
            checkClient(context, endUser, clientHandler -> {
                if (clientHandler.failed()) {
                    handler.handle(Future.failedFuture(clientHandler.cause()));
                    return;
                }
                // continue
                handler.handle(Future.succeededFuture());
            });
        });
    }

    private void checkAccountStatus(RoutingContext context, io.gravitee.am.model.User user, Handler<AsyncResult<Void>> handler) {
        // if user is disabled, sign out the user
        if (!user.isEnabled()) {
            handler.handle(Future.failedFuture(new AccountDisabledException(user.getId())));
            return;
        }
        CookieSession session = (CookieSession) context.session().getDelegate();
        var dates = List.of(
                // if user has reset its password, check the last login date to make sure that the current session is not compromised
                ofNullable(user.getLastPasswordReset()),
                // We check if the username has been reset and invalidate the session
                ofNullable(user.getLastUsernameReset()),
                // if user has been sign out in a REST manner way, check the last login date to make sure that the current session is not compromised
                ofNullable(user.getLastLogoutAt())
        );

        final long sessionTime = session.lastLogin().getTime();
        final boolean isIllegalStateAccount = dates.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Date::getTime)
                // "exp" claim is stored in epoch seconds format in the cookie session
                // we need to compare both dates without the milliseconds
                .anyMatch(time -> time - sessionTime > 1000L);

        if (isIllegalStateAccount) {
            handler.handle(Future.failedFuture(new AccountIllegalStateException(user.getId())));
        } else {
            handler.handle(Future.succeededFuture());
        }
    }

    private void checkClient(RoutingContext context, io.gravitee.am.model.User user, Handler<AsyncResult<Void>> handler) {
        final String clientId = context.request().getParam(Parameters.CLIENT_ID);
        // no client to check, continue
        if (clientId == null) {
            handler.handle(Future.succeededFuture());
            return;
        }
        // no client to check for the user, continue
        if (user.getClient() == null) {
            handler.handle(Future.succeededFuture());
            return;
        }
        // check if both clients (requested and user client) share the same identity provider
        Single.zip(getClient(clientId), getClient(user.getClient()), (optRequestedClient, optUserClient) -> {
                    Client requestedClient = optRequestedClient.get();
                    Client userClient = optUserClient.get();

                    // no client to check, continue
                    if (requestedClient == null) {
                        return Completable.complete();
                    }

                    // no client to check for the user, continue
                    if (userClient == null) {
                        return Completable.complete();
                    }

                    // if same client, nothing to do, continue
                    if (userClient.getId().equals(requestedClient.getId())) {
                        return Completable.complete();
                    }

                    Set<String> userIdentities = ofNullable(user.getIdentities()).orElseGet(List::of).stream().map(UserIdentity::getProviderId).collect(Collectors.toSet());
                    userIdentities.add(user.getSource());

                    // both clients are sharing the same provider, continue
                    if (nonNull(requestedClient.getIdentityProviders()) &&
                            requestedClient.getIdentityProviders().stream()
                                    .anyMatch(appIdp -> userIdentities.contains(appIdp.getIdentity()))) {

                        // check brute force status to be sure that an active session on another client bypass the BlockedUser status
                        final AccountSettings accountSettings = AccountSettings.getInstance(domain, requestedClient);
                        if (accountSettings != null && accountSettings.isLoginAttemptsDetectionEnabled()) {
                            LoginAttemptCriteria criteria = new LoginAttemptCriteria.Builder()
                                    .domain(domain.getId())
                                    .client(requestedClient.getId())
                                    .identityProvider(user.getSource())
                                    .username(user.getUsername())
                                    .build();
                            return loginAttemptService
                                    .checkAccount(domain, criteria, accountSettings)
                                    .map(loginAttempt -> {
                                        if (loginAttempt.isAccountLocked(accountSettings.getMaxLoginAttempts())) {
                                            Map<String, String> details = new HashMap<>();
                                            details.put("attempt_id", loginAttempt.getId());
                                            throw new AccountLockedException("User " + user.getUsername() + " is locked", details);
                                        }
                                        return loginAttempt;
                                    }).ignoreElement();
                        }
                        return Completable.complete();
                    }

                    // throw error
                    throw new InvalidRequestException("User is not on a shared identity provider");
                })
                .subscribe(
                        completable -> completable.subscribe(() -> handler.handle(Future.succeededFuture()), error -> handler.handle(Future.failedFuture(error))),
                        error -> handler.handle(Future.failedFuture(error)));
    }

    private Single<Optional<Client>> getClient(String clientId) {
        return clientSyncService.findById(clientId)
                .switchIfEmpty(Maybe.defer(() -> clientSyncService.findByClientId(clientId)))
                .map(Optional::ofNullable)
                .defaultIfEmpty(Optional.empty());
    }
}
