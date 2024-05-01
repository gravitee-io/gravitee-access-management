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
package io.gravitee.am.gateway.handler.root.service.user;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.Client;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserRegistrationIdpResolver {
    private static final String DEFAULT_IDP_PREFIX = "default-idp-";


    public static String getRegistrationIdpForUser(Domain domain, Client client, User user) {
        var accountSettings = AccountSettings.getInstance(client, domain);
        return accountSettings
                .map(AccountSettings::getDefaultIdentityProviderForRegistration)
                .orElseGet(() -> user.getSource() == null ? DEFAULT_IDP_PREFIX + domain.getId() : user.getSource());
    }

    public static String getRegistrationIdp(Domain domain, Client client) {
        var accountSettings = AccountSettings.getInstance(client, domain);
        return accountSettings
                .map(AccountSettings::getDefaultIdentityProviderForRegistration)
                .orElseGet(() -> DEFAULT_IDP_PREFIX + domain.getId());
    }
}
