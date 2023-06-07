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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.User;
import io.vertx.core.json.DecodeException;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.springframework.util.StringUtils;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountWebAuthnCredentialsEndpointHandler {

    private AccountService accountService;

    public AccountWebAuthnCredentialsEndpointHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Get enrolled WebAuthn credentials for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void listEnrolledWebAuthnCredentials(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        accountService.getWebAuthnCredentials(user)
                        .subscribe(
                                enrolledCredentials -> AccountResponseHandler.handleDefaultResponse(routingContext, enrolledCredentials),
                                error -> routingContext.fail(error)
                        );
    }

    /**
     * Get enrolled WebAuthn credential detail for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void getEnrolledWebAuthnCredential(RoutingContext routingContext) {
        final String credentialId = routingContext.request().getParam("credentialId");

        accountService.getWebAuthnCredential(credentialId)
                .subscribe(
                        credential -> AccountResponseHandler.handleDefaultResponse(routingContext, credential),
                        error -> routingContext.fail(error)
                );
    }

    /**
     * Remove enrolled WebAuthn credential for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void removeEnrolledWebAuthnCredential(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final String credentialId = routingContext.request().getParam("credentialId");

        accountService.removeWebAuthnCredential(user.getId(), credentialId, new DefaultUser(user))
                .subscribe(
                        () -> AccountResponseHandler.handleNoBodyResponse(routingContext),
                        error -> routingContext.fail(error)
                );
    }

    /**
     * Update enrolled WebAuthn credential for the current user
     *
     * @param routingContext the routingContext holding the current user
     */
    public void updateEnrolledWebAuthnCredential(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final String credentialId = routingContext.request().getParam("credentialId");

        try {
            if (routingContext.body().asString() == null) {
                routingContext.fail(new InvalidRequestException("Unable to parse body message"));
                return;
            }

            final String deviceName = routingContext.body().asJsonObject().getString("deviceName");

            // deviceName is required
            if (StringUtils.isEmpty(deviceName)) {
                routingContext.fail(new InvalidRequestException("Field [deviceName] is required"));
                return;
            }

            accountService.updateWebAuthnCredential(user.getId(), credentialId, deviceName, new DefaultUser(user))
                    .subscribe(
                            credential -> AccountResponseHandler.handleDefaultResponse(routingContext, credential),
                            error -> routingContext.fail(error)
                    );
        } catch (DecodeException ex) {
            routingContext.fail(new InvalidRequestException("Unable to parse body message"));
        }
    }
}
