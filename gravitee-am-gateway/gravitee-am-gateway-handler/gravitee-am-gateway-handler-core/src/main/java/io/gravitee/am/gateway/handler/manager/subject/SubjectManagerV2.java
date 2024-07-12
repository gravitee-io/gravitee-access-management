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

package io.gravitee.am.gateway.handler.manager.subject;


import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Maybe;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@AllArgsConstructor
public class SubjectManagerV2 implements SubjectManager {

    private static final String SEPARATOR = "|";

    private UserService userService;

    private Domain domain;

    @Override
    public String generateSubFrom(User user) {
        return user.getSource() + SEPARATOR + user.getExternalId();
    }

    @Override
    public Maybe<User> findUserBySub(String sub) {
        if (isCompound(sub)) {
            log.error("malformed sub value '{}'", sub);
            return Maybe.error(new IllegalArgumentException("malformed sub value"));
        }

        final var source = sub.substring(0, sub.indexOf(SEPARATOR));
        final var extId = sub.substring(sub.indexOf(SEPARATOR)+1);
        return userService.findByDomainAndExternalIdAndSource(domain.getId(), extId, source);
    }

    private boolean isCompound(String sub) {
        return sub.indexOf(SEPARATOR) <= 0;
    }

    @Override
    public Maybe<String> findUserIdBySub(String sub) {
        return this.findUserBySub(sub).map(User::getId);
    }

    @Override
    public Maybe<io.gravitee.am.identityprovider.api.User> getPrincipal(String sub) {
        if (isCompound(sub)) {
            // if sub doesn't match the v2 format
            // return empty as the action is probably managed by a client app
            return Maybe.empty();
        }
        return findUserBySub(sub)
                .map(principal -> new DefaultUser(principal));
    }
}
