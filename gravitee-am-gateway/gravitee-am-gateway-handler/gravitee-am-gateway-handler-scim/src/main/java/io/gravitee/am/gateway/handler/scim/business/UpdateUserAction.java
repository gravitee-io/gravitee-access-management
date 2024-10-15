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

package io.gravitee.am.gateway.handler.scim.business;


import io.gravitee.am.common.scim.Schema;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.model.User;
import io.gravitee.am.gateway.handler.scim.service.UserService;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class UpdateUserAction extends AbstractUserAction {

    public UpdateUserAction(UserService userService, Domain domain, Client client) {
        super(userService, domain, client);
    }

    public Single<User> execute(String userId, String baseUrl, Map<String, Object> payload, AuthenticationContext authenticationContext, io.gravitee.am.identityprovider.api.User principal) {
        final User user = extractUser(payload);

        // username is required
        if (user.getUserName() == null || user.getUserName().isBlank()) {
            return Single.error(new InvalidValueException("Field [userName] is required"));
        }

        // schemas field is REQUIRED and MUST contain valid values and MUST not contain duplicate values
        try {
            checkSchemas(user.getSchemas(), Schema.supportedSchemas());
        } catch (Exception ex) {
            return Single.error(ex);
        }

        // handle identity provider source
        return userSource(authenticationContext)
                .toSingle()
                .flatMap(optSource -> userService.update(userId, user, optSource.orElse(null), baseUrl, principal, client));
    }
}
