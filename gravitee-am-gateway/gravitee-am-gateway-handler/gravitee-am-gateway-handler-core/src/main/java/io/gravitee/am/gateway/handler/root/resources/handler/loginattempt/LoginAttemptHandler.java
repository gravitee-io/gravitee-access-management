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

package io.gravitee.am.gateway.handler.root.resources.handler.loginattempt;

import io.gravitee.am.dataplane.api.search.LoginAttemptCriteria;
import io.gravitee.am.dataplane.api.search.LoginAttemptCriteria.Builder;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.service.LoginAttemptGatewayService;
import io.gravitee.am.gateway.handler.common.service.UserActivityGatewayService;
import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.LoginAttempt;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.LOGIN_ATTEMPT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USERNAME_PARAM_KEY;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;


/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginAttemptHandler implements Handler<RoutingContext> {

    private final Domain domain;
    private final IdentityProviderManager identityProviderManager;
    private final LoginAttemptGatewayService loginAttemptService;
    private final UserActivityGatewayService userActivityService;

    public LoginAttemptHandler(
            Domain domain,
            IdentityProviderManager identityProviderManager,
            LoginAttemptGatewayService loginAttemptService,
            UserActivityGatewayService userActivityService
    ) {
        this.domain = domain;
        this.identityProviderManager = identityProviderManager;
        this.loginAttemptService = loginAttemptService;
        this.userActivityService = userActivityService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = ofNullable(routingContext.<Client>get(CLIENT_CONTEXT_KEY)).orElse(new Client());
        String adaptiveRule = getAdaptiveRule(client);
        var username = routingContext.request().getParam(USERNAME_PARAM_KEY);
        if (canApplyLoginAttemptKey(client, adaptiveRule, username)) {
            var accountSettings = AccountSettings.getInstance(domain, client);
            var optionalLoginAttempt = getLoginAttempt(client, username, accountSettings);
            optionalLoginAttempt.doOnSuccess(loginAttempt ->
                    loginAttempt.ifPresent(attempt -> routingContext.session().put(LOGIN_ATTEMPT_KEY, attempt.getAttempts()))
            ).doFinally(routingContext::next).subscribe();
        } else {
            routingContext.next();
        }
    }

    private String getAdaptiveRule(Client client) {
        return ofNullable(client.getMfaSettings()).filter(Objects::nonNull)
                .map(MFASettings::getChallenge)
                .map(ChallengeSettings::getChallengeRule)
                .orElse("");
    }

    private boolean canApplyLoginAttemptKey(Client client, String adaptiveRule, String username) {
        return !isNullOrEmpty(client.getId()) &&
                (!isNullOrEmpty(adaptiveRule) || userActivityService.canSaveUserActivity()) &&
                !isNullOrEmpty(username);
    }

    private Maybe<Optional<LoginAttempt>> getLoginAttempt(Client client, String username, AccountSettings accountSettings) {
        return ofNullable(client.getIdentityProviders()).orElse(new TreeSet<>()).stream()
                .map(ApplicationIdentityProvider::getIdentity)
                .map(identityProviderManager::getIdentityProvider)
                .filter(Objects::nonNull).filter(not(IdentityProvider::isExternal))
                .map(IdentityProvider::getId)
                .map(idp -> new Builder().domain(domain.getId()).client(client.getId()).username(username).identityProvider(idp))
                .map(Builder::build)
                .map(criteria -> getLoginAttempt(accountSettings, criteria))
                .findFirst().orElse(Maybe.empty());
    }

    private Maybe<Optional<LoginAttempt>> getLoginAttempt(AccountSettings accountSettings, LoginAttemptCriteria criteria) {
        return loginAttemptService.checkAccount(domain, criteria, accountSettings).map(Optional::ofNullable).switchIfEmpty(Maybe.just(Optional.empty()));
    }
}
