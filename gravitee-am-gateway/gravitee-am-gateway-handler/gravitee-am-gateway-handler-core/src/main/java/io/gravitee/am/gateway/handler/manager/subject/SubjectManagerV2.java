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


import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.client.ClientManager;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Maybe;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@AllArgsConstructor
public class SubjectManagerV2 implements SubjectManager {

    private static final String SEPARATOR = ":";

    private UserService userService;

    private ClientManager clientManager;

    private Domain domain;

    @Override
    public String generateSubFrom(User user) {
        return UUID.nameUUIDFromBytes(generateInternalSubFrom(user).getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Override
    public String generateInternalSubFrom(User user) {
        return user.getSource() + SEPARATOR + user.getExternalId();
    }

    @Override
    public void updateJWT(JWT jwt, User user) {
        if (clientManager.get(user.getId()) != null) { //This is for extension grant because we cannot do distinguish between service and user profile
            jwt.setSub(user.getId());
        } else {
            jwt.setInternalSub(generateInternalSubFrom(user));
            jwt.setSub(generateSubFrom(user));
        }
    }

    @Override
    public Maybe<User> findUserBySub(JWT token) {
        if (!hasValidInternalSub(token)) {
            log.error("malformed internal sub value '{}'", token);
            return Maybe.error(new IllegalArgumentException("Required internal sub value is missing"));
        }

        final var internalSub = token.getInternalSub();
        final var source = internalSub.substring(0, internalSub.indexOf(SEPARATOR));
        final var extId = internalSub.substring(internalSub.indexOf(SEPARATOR) + 1);
        return userService.findByDomainAndExternalIdAndSource(domain.getId(), extId, source);
    }

    private boolean hasValidInternalSub(JWT token) {
        return StringUtils.hasLength(token.getInternalSub()) && token.getInternalSub().contains(SEPARATOR);
    }

    @Override
    public Maybe<String> findUserIdBySub(JWT sub) {
        return this.findUserBySub(sub).map(User::getId);
    }

    @Override
    public Maybe<io.gravitee.am.identityprovider.api.User> getPrincipal(JWT sub) {
        if (!hasValidInternalSub(sub)) {
            // if sub doesn't match the v2 format
            // return empty as the action is probably managed by a client app
            return Maybe.empty();
        }
        return findUserBySub(sub)
                .map(DefaultUser::new);
    }

    @Override
    public String extractUserId(String gis) {
        return gis.substring(gis.indexOf(SEPARATOR) + 1);
    }
}
