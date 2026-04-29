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

import io.gravitee.am.common.exception.authentication.LoginCallbackFailedException;
import io.gravitee.am.identityprovider.api.User;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

class UsersDomainWhitelistValidator {

    private static final Logger logger = LoggerFactory.getLogger(UsersDomainWhitelistValidator.class);

    Single<User> checkDomainWhitelist(User endUser, List<String> domainWhitelist) {
        // No whitelist means we let everyone, so we reject the connection if neither the username nor the email are allowed
        if(domainWhitelist == null || domainWhitelist.isEmpty()) {
            return Single.just(endUser);
        }
        if (isDomainNotWhitelisted(endUser.getUsername(), domainWhitelist)
                && isDomainNotWhitelisted(endUser.getEmail(), domainWhitelist)) {
            return Single.error(new LoginCallbackFailedException("could not authenticate user"));
        }

        return Single.just(endUser);
    }

    private static boolean isDomainNotWhitelisted(String identifier, List<String> domainWhitelist) {
        if (identifier == null) {
            return true;
        }
        var domainName = identifier.split("@");
        // identifier is not an email, fail
        if (domainName.length < 2) {
            logger.debug("Identifier [{}] is not an email", identifier);
            return true;
        }

        // RFC 4343: DNS labels are case-insensitive, so domain comparison must be too
        if (domainWhitelist.stream().noneMatch(domainName[1].trim()::equalsIgnoreCase)) {
            logger.debug("Identifier [{}] does not match domainWhitelist", identifier);
            return true;
        }
        return false;
    }

}
