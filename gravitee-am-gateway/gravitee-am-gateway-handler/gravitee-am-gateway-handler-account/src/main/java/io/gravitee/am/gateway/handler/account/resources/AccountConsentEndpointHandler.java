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
package io.gravitee.am.gateway.handler.account.resources;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountConsentEndpointHandler {

    private AccountService accountService;

    public AccountConsentEndpointHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Get list of consent for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void listConsent(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        accountService.getConsentList(user, client)
                        .subscribe(
                                consentList -> AccountResponseHandler.handleDefaultResponse(routingContext, consentList),
                                error -> routingContext.fail(error)
                        );
    }

    /**
     * Get consent detail for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void getConsent(RoutingContext routingContext) {
        final String consentId = routingContext.request().getParam("consentId");

        accountService.getConsent(consentId)
                .subscribe(
                        consent -> AccountResponseHandler.handleDefaultResponse(routingContext, consent),
                        error -> routingContext.fail(error)
                );
    }

    /**
     * Remove consent for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void removeConsent(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final String consentId = routingContext.request().getParam("consentId");

        accountService.removeConsent(user.getId(), consentId, new DefaultUser(user))
                .subscribe(
                        () -> AccountResponseHandler.handleNoBodyResponse(routingContext),
                        error -> routingContext.fail(error)
                );
    }

}
