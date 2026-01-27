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


import io.gravitee.am.common.exception.jwt.InvalidGISException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.common.user.impl.UserGatewayServiceImplV2;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.reactivex.rxjava3.core.Maybe;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static io.gravitee.am.gateway.handler.common.user.impl.UserGatewayServiceImplV2.SEPARATOR;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@AllArgsConstructor
public class SubjectManagerV2 implements SubjectManager {

    private UserGatewayService userService;

    private Domain domain;

    @Override
    public String generateSubFrom(UserId userId) {
        return UUID.nameUUIDFromBytes(generateInternalSubFrom(userId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Override
    public String generateInternalSubFrom(UserId user) {
        return UserGatewayServiceImplV2.generateInternalSubFrom(user.source(), user.externalId());
    }

    @Override
    public void updateJWT(JWT jwt, User user) {
        if (user.getSource() == null ) {
            // This is for extension grant because we cannot do distinguish between service and user profile
            // in the ExtensionGrant implementation for V2 domain,
            // if Create or Verify Account is enabled the source will be present
            // as we are playing user profile
            // otherwise we just want to generate a token
            // based on the assertion, in that cas we only set the sub equals to the userId
            jwt.setSub(user.getId());
        } else {
            jwt.setInternalSub(generateInternalSubFrom(user.getFullId()));
            jwt.setSub(generateSubFrom(user.getFullId()));
        }
    }

    @Override
    public Maybe<User> findUserBySub(JWT token) {
        if (!hasValidInternalSub(token)) {
            log.error("malformed internal sub value '{}'", token);
            return Maybe.error(new InvalidGISException("Required internal sub value is missing"));
        }

        final var internalSub = token.getInternalSub();
        final var source = internalSub.substring(0, internalSub.indexOf(SEPARATOR));
        final var extId = internalSub.substring(internalSub.indexOf(SEPARATOR) + 1);
        return userService.findByExternalIdAndSource(extId, source);
    }

    private boolean hasValidInternalSub(JWT token) {
        return hasValidInternalSub(token.getInternalSub());
    }

    @Override
    public boolean hasValidInternalSub(String internalSub) {
        return StringUtils.hasLength(internalSub) && internalSub.contains(SEPARATOR);
    }

    @Override
    public Maybe<UserId> findUserIdBySub(JWT sub) {
        return this.findUserBySub(sub).map(User::getFullId);
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

    @Override
    public String extractSourceId(String gis) {
        return gis.substring(0, gis.indexOf(SEPARATOR));
    }
}
