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
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.exception.authentication.AccountDisabledException;
import io.gravitee.am.common.exception.authentication.AccountEnforcePasswordException;
import io.gravitee.am.common.exception.authentication.AccountLockedException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.dataplane.api.search.LoginAttemptCriteria;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationService;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserIdentity;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY;
import static io.gravitee.am.service.utils.UserProfileUtils.buildDisplayName;
import static io.gravitee.am.service.utils.UserProfileUtils.hasGeneratedDisplayName;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuthenticationServiceImpl implements UserAuthenticationService {

    private static final String SOURCE_FIELD = "source";
    private static final String LAST_IDENTITY_FIELD = "last_identity";
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private Domain domain;

    @Autowired
    private UserGatewayService userService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private RulesEngine rulesEngine;

    @Autowired
    private SubjectManager subjectManager;

    @Override
    public Single<User> connect(io.gravitee.am.identityprovider.api.User principal,
                                Client client,
                                Request request,
                                boolean afterAuthentication) {

        // fire PRE_CONNECT flow
        final User preConnectedUser = create0(principal, afterAuthentication);
        return rulesEngine.fire(ExtensionPoint.PRE_CONNECT, request, client, preConnectedUser)
                .flatMap(executionContext -> {
                    return saveOrUpdate(preConnectedUser, executionContext, afterAuthentication)
                            // check account status
                            .flatMap(user -> checkAccountStatus(user)
                                    // and enhance user information
                                    .andThen(Single.defer(() -> userService.enhance(user))));
                })
                // fire POST_CONNECT flow
                .flatMap(endUser -> rulesEngine.fire(ExtensionPoint.POST_CONNECT, request, client, endUser).map(__ -> endUser));
    }

    @Override
    public Single<User> connectWithPasswordless(String subject, Client client) {
        return userService.findById(subject)
                .switchIfEmpty(Single.error(() -> new UserNotFoundException(subject)))
                // check account status
                .flatMap(user -> {
                    if (isIndefinitelyLocked(user) || (user.getAccountLockedUntil() != null && user.getAccountLockedUntil().after(new Date()))) {
                        return Single.error(new AccountLockedException("Account is locked for user " + user.getUsername()));
                    }
                    if (!user.isEnabled()) {
                        return Single.error(new AccountDisabledException("Account is disabled for user " + user.getUsername()));
                    }
                    return Single.just(user);
                })
                // check passwordless enforce password policy
                .flatMap(user -> {
                    final LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
                    final Date lastLoginWithCredentials = user.getLastLoginWithCredentials();
                    // no last login date, continue
                    if (lastLoginWithCredentials == null) {
                        return Single.just(user);
                    }
                    // feature disabled, continue
                    if (loginSettings == null || !loginSettings.isEnforcePasswordPolicyEnabled()) {
                        return Single.just(user);
                    }
                    // evaluate the condition
                    final Integer maxAge = loginSettings.getPasswordlessEnforcePasswordMaxAge();
                    final Instant expirationDate = lastLoginWithCredentials.toInstant().plusSeconds(maxAge);
                    if (expirationDate.isBefore(Instant.now())) {
                        return Single.error(new AccountEnforcePasswordException("User credentials are required"));
                    }
                    return Single.just(user);
                })
                // update login information
                .flatMap(user -> {
                    user.setLoggedAt(new Date());
                    user.setLoginsCount(user.getLoginsCount() + 1);
                    // initialize the last login with credentials date only if it's not set
                    // useful if the enforce password for passwordless feature is enabled
                    if (user.getLastLoginWithCredentials() == null) {
                        user.setLastLoginWithCredentials(user.getLoggedAt());
                    }
                    return userService.update(user)
                            .flatMap(user1 -> userService.enhance(user1));
                });
    }

    @Override
    public Maybe<User> loadPreAuthenticatedUser(String subject, Request request) {
        return internalLoadPreAuthenticateUser(subject, request, userService.findById(subject));
    }

    @Override
    public Maybe<User> loadPreAuthenticatedUserBySub(JWT token, Request request) {
        return internalLoadPreAuthenticateUser(token.getSub(), request, subjectManager.findUserBySub(token));
    }

    private Maybe<User> internalLoadPreAuthenticateUser(String subject, Request request, Maybe<User> maybeFindUser) {
        return maybeFindUser
                .switchIfEmpty(Maybe.error(() -> new UserNotFoundException(subject)))
                .flatMap(user -> isIndefinitelyLocked(user) ?
                        Maybe.error(new AccountLockedException("User " + user.getUsername() + " is locked")) :
                        Maybe.just(user)
                )
                .flatMap(user -> identityProviderManager.get(user.getLastIdentityUsed())
                        // if the user has been found, try to load user information from its latest identity provider
                        .flatMap(authenticationProvider -> {
                            SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(request);
                            final Authentication authentication = new EndUserAuthentication(user, null, authenticationContext);
                            return authenticationProvider.loadPreAuthenticatedUser(authentication);
                        })
                        .flatMap(idpUser -> {
                            // retrieve information from the idp user and update the user
                            Map<String, Object> additionalInformation = idpUser.getAdditionalInformation() == null ? new HashMap<>() : new HashMap<>(idpUser.getAdditionalInformation());
                            additionalInformation.put(SOURCE_FIELD, user.getSource());
                            additionalInformation.put(LAST_IDENTITY_FIELD, user.getLastIdentityUsed());
                            additionalInformation.put(Parameters.CLIENT_ID, user.getClient());
                            ((DefaultUser) idpUser).setAdditionalInformation(additionalInformation);
                            final User preConnectedUser = create0(idpUser, false);
                            return update(user, preConnectedUser, false)
                                    .flatMap(userService::enhance).toMaybe();
                        })
                        // no user has been found in the identity provider, just enhance user information
                        .switchIfEmpty(Maybe.defer(() -> userService.enhance(user).toMaybe())));
    }

    @Override
    public Completable lockAccount(LoginAttemptCriteria criteria, AccountSettings accountSettings, Client client, User user) {
        if (user == null) {
            return Completable.complete();
        }

        // update user status
        user.setAccountNonLocked(false);
        user.setAccountLockedAt(new Date());
        user.setAccountLockedUntil(new Date(System.currentTimeMillis() + (accountSettings.getAccountBlockedDuration() * 1000)));

        return userService.update(user)
                .flatMap(user1 -> {
                    // send an email if option is enabled
                    if (user1.getEmail() != null && accountSettings.isSendRecoverAccountEmail()) {
                        new Thread(() -> emailService.send(Template.BLOCKED_ACCOUNT, user1, client)).start();
                    }
                    return Single.just(user);
                })
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class)
                        .type(EventType.USER_LOCKED)
                        .reference(Reference.domain(criteria.domain()))
                        .client(criteria.client())
                        .principal(null)
                        .user(user1)))
                .ignoreElement();
    }

    private Single<User> saveOrUpdate(User preConnectedUser, ExecutionContext executionContext, boolean afterAuthentication) {
        final String source = preConnectedUser.getSource();
        final String externalId = preConnectedUser.getExternalId();
        final String username = preConnectedUser.getUsername();
        final String linkedAccount = (String) executionContext.getAttribute(ConstantKeys.LINKED_ACCOUNT_ID_CONTEXT_KEY);
        final boolean accountLinkingMode = linkedAccount != null;
        final Maybe<User> findExistingUser =
                accountLinkingMode ? userService.findById(linkedAccount) :
                        userService.findByExternalIdAndSource(externalId, source)
                                .switchIfEmpty(Maybe.defer(() -> userService.findByUsernameAndSource(username, source)));

        return findExistingUser
                .switchIfEmpty(Single.error(() -> new UserNotFoundException(username)))
                .flatMap(user -> isIndefinitelyLocked(user) ?
                        Single.error(new AccountLockedException("User " + user.getUsername() + " is locked")) :
                        Single.just(user)
                )
                .flatMap(existingUser -> update(existingUser, preConnectedUser, afterAuthentication, accountLinkingMode))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof UserNotFoundException) {
                        return create(preConnectedUser);
                    }
                    return Single.error(ex);
                });
    }

    private boolean isIndefinitelyLocked(User user) {
        return !user.isAccountNonLocked() && user.getAccountLockedUntil() == null;
    }

    /**
     * Check the user account status
     * @param user Authenticated user
     * @return Completable.complete() or Completable.error(error) if account status is not ok
     */
    private Completable checkAccountStatus(User user) {
        if (!user.isEnabled()) {
            return Completable.error(new AccountDisabledException("Account is disabled for user " + user.getUsername()));
        }
        return Completable.complete();
    }

    /**
     * Update user information with data from the identity provider user
     * @param existingUser existing user in the repository
     * @param preConnectedUser user from the identity provider
     * @param afterAuthentication if update operation is called after a sign in operation
     * @return updated user
     */
    private Single<User> update(User existingUser,
                                User preConnectedUser,
                                boolean afterAuthentication) {
        return update(existingUser, preConnectedUser, afterAuthentication, null);
    }

    /**
     * Update user information with data from the identity provider user
     * @param existingUser existing user in the repository
     * @param preConnectedUser user from the identity provider
     * @param afterAuthentication if update operation is called after a sign in operation
     * @param accountLinking if update operation is called on a linked account
     * @return updated user
     */
    private Single<User> update(User existingUser,
                                User preConnectedUser,
                                boolean afterAuthentication,
                                Boolean accountLinking) {
        LOGGER.debug("Updating user: username[{}]", preConnectedUser.getUsername());

        var updateActions = UserRepository.UpdateActions.none();

        // update authentication information
        if (afterAuthentication) {
            existingUser.setLastIdentityUsed(preConnectedUser.getSource());
            existingUser.setLoggedAt(new Date());
            existingUser.setLastLoginWithCredentials(existingUser.getLoggedAt());
            existingUser.setLoginsCount(existingUser.getLoginsCount() + 1);
            existingUser.setAccountNonLocked(true);
        }

        // set client
        if (preConnectedUser.getClient() !=  null) {
            existingUser.setClient(preConnectedUser.getClient());
        }

        // check if it's a linked account
        if (isAccountLinked(accountLinking, existingUser, preConnectedUser)) {
            upsertLinkedIdentities(existingUser, preConnectedUser);
            updateActions.updateIdentities(true);
        } else {
            // set external id
            existingUser.setExternalId(preConnectedUser.getExternalId());

            // set profile name information
            if (!isNullOrEmpty(preConnectedUser.getEmail())) {
                existingUser.setEmail(preConnectedUser.getEmail());
            }
            if (!isNullOrEmpty(preConnectedUser.getLastName())) {
                existingUser.setLastName(preConnectedUser.getLastName());
            }
            if (!isNullOrEmpty(preConnectedUser.getFirstName())) {
                existingUser.setFirstName(preConnectedUser.getFirstName());
            }
            if (existingUser.getFirstName() != null && hasGeneratedDisplayName(existingUser)) {
                existingUser.setDisplayName(buildDisplayName(existingUser));
            }

            // set roles and groups
            updateActions.updateDynamicRole(!Objects.equals(existingUser.getDynamicRoles(), preConnectedUser.getDynamicRoles()));
            updateActions.updateDynamicGroup(!Objects.equals(existingUser.getDynamicGroups(), preConnectedUser.getDynamicGroups()));
            existingUser.setDynamicRoles(preConnectedUser.getDynamicRoles());
            existingUser.setDynamicGroups(preConnectedUser.getDynamicGroups());

            // set additional information
            Map<String, Object> additionalInformation = ofNullable(preConnectedUser.getAdditionalInformation()).orElse(Map.of());
            removeOriginalProviderOidcTokensIfNecessary(existingUser, afterAuthentication, additionalInformation);
            extractAdditionalInformation(existingUser, additionalInformation);
        }

        // populate last password reset if missing
        if (existingUser.getLastPasswordReset() == null) {
            existingUser.setLastPasswordReset(existingUser.getUpdatedAt() == null ? new Date() : existingUser.getUpdatedAt());
        }

        return userService.update(existingUser, updateActions);
    }

    private void removeOriginalProviderOidcTokensIfNecessary(User existingUser, boolean afterAuthentication, Map<String, Object> additionalInformation) {
        if (afterAuthentication) {
            // remove the op_id_token and op_access_token from existing user profile to avoid keeping this information
            // if the singleSignOut is disabled or provider does not retrieve oidc tokens
            if (!additionalInformation.containsKey(OIDC_PROVIDER_ID_TOKEN_KEY)) {
                existingUser.removeAdditionalInformation(OIDC_PROVIDER_ID_TOKEN_KEY);
            }
            if (!additionalInformation.containsKey(OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY)) {
                existingUser.removeAdditionalInformation(OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY);
            }
        }
    }

    /**
     * Create user with data from the identity provider user
     * @param preConnectedUser user from the identity provider
     * @return created user
     */
    private Single<User> create(User preConnectedUser) {
        LOGGER.debug("Creating a new user: username[%s]", preConnectedUser.getUsername());
        return userService.create(preConnectedUser);
    }

    protected User create0(io.gravitee.am.identityprovider.api.User principal, boolean afterAuthentication) {
        final User preConnectedUser = new User();
        preConnectedUser.setExternalId(principal.getId());
        preConnectedUser.setUsername(principal.getUsername());
        preConnectedUser.setEmail(principal.getEmail());
        preConnectedUser.setFirstName(principal.getFirstName());
        preConnectedUser.setLastName(principal.getLastName());
        preConnectedUser.setReferenceType(ReferenceType.DOMAIN);
        preConnectedUser.setReferenceId(domain.getId());
        preConnectedUser.setSource((String) principal.getAdditionalInformation().get(SOURCE_FIELD));
        if (afterAuthentication) {
            preConnectedUser.setLastIdentityUsed(preConnectedUser.getSource());
            preConnectedUser.setLoggedAt(new Date());
            preConnectedUser.setLastLoginWithCredentials(preConnectedUser.getLoggedAt());
            preConnectedUser.setLoginsCount(1L);
        }

        if (principal.getAdditionalInformation().get(LAST_IDENTITY_FIELD) != null) {
           preConnectedUser.setLastIdentityUsed(principal.getAdditionalInformation().get(LAST_IDENTITY_FIELD).toString());
        }

        preConnectedUser.setDynamicRoles(principal.getRoles());
        preConnectedUser.setDynamicGroups(principal.getGroups());

        Map<String, Object> additionalInformation = principal.getAdditionalInformation();
        extractAdditionalInformation(preConnectedUser, additionalInformation);
        return preConnectedUser;
    }

    private void extractAdditionalInformation(User user, Map<String, Object> additionalInformation) {
        if (additionalInformation != null) {
            Map<String, Object> extraInformation = user.getAdditionalInformation() != null ? new HashMap<>(user.getAdditionalInformation()) : new HashMap<>();
            extraInformation.putAll(additionalInformation);
            if (user.getLoggedAt() != null) {
                extraInformation.put(Claims.AUTH_TIME, user.getLoggedAt().getTime() / 1000);
            }
            if (user.getUsername() != null) {
                extraInformation.put(StandardClaims.PREFERRED_USERNAME, user.getUsername());
            }
            if (extraInformation.get(SOURCE_FIELD) != null) {
                user.setSource((String) extraInformation.remove(SOURCE_FIELD));
            }
            if (extraInformation.get(Parameters.CLIENT_ID) != null) {
                user.setClient((String) extraInformation.remove(Parameters.CLIENT_ID));
            }
            user.setAdditionalInformation(extraInformation);
        }
    }

    private boolean isAccountLinked(Boolean accountLinking,
                                    User existingUser,
                                    User preConnectedUser) {
        if (Boolean.TRUE.equals(accountLinking)) {
            return true;
        }

        // If we have a last identity configured, use that for the link query
        final String providerId = Strings.isNullOrEmpty(preConnectedUser.getLastIdentityUsed())
                ? preConnectedUser.getSource()
                : preConnectedUser.getLastIdentityUsed();

        return ofNullable(existingUser.getIdentities())
                .orElse(List.of())
                .stream()
                .anyMatch(u -> u.getUserId().equals(preConnectedUser.getExternalId()) && u.getProviderId().equals(providerId));
    }

    private void upsertLinkedIdentities(User existingUser, User preConnectedUser) {
        List<UserIdentity> userIdentities =
                existingUser.getIdentities() != null ? new ArrayList<>(existingUser.getIdentities()) : null;
        if (userIdentities == null || userIdentities.isEmpty()) {
            userIdentities = Collections.singletonList(userIdentity(preConnectedUser));
        } else {
            var linkedAccount = userIdentities.stream()
                    .filter(u -> u.getUserId().equals(preConnectedUser.getExternalId()))
                    .findFirst();
            if (linkedAccount.isPresent()) {
                // only update the additionalInformation
                // and username as it is part of the additionalInformation
                var userIdentity = new UserIdentity(linkedAccount.get());
                userIdentity.setUsername(preConnectedUser.getUsername());
                userIdentity.setAdditionalInformation(preConnectedUser.getAdditionalInformation());
                userIdentities.removeIf(u -> u.getUserId().equals(userIdentity.getUserId()));
                userIdentities.add(userIdentity);
            } else {
                userIdentities.add(userIdentity(preConnectedUser));
            }
        }
        existingUser.setIdentities(userIdentities);
    }

    private UserIdentity userIdentity(User preConnectedUser) {
        UserIdentity userIdentity = new UserIdentity();
        userIdentity.setUserId(preConnectedUser.getExternalId());
        userIdentity.setUsername(preConnectedUser.getUsername());
        userIdentity.setProviderId(preConnectedUser.getSource());
        userIdentity.setAdditionalInformation(preConnectedUser.getAdditionalInformation());
        userIdentity.setLinkedAt(new Date());
        return userIdentity;
    }
}
