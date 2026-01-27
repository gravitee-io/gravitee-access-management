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

package io.gravitee.am.gateway.handler.common.jwt;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.reactivex.rxjava3.core.Maybe;

public interface SubjectManager {


    /**
     * @return `sub` claim value generated from the user's id
     */
    String generateSubFrom(UserId user);

    /**
     * @deprecated use {@link #generateSubFrom(UserId)} directly
     * @return `sub` claim value generated from the user's id
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    default String generateSubFrom(User user) {
        return generateSubFrom(user.getFullId());
    }

    /**
     * @return `gis` claim value generated from the user's id
     */
    String generateInternalSubFrom(UserId userId);

    /**
     * @deprecated use {@link #generateInternalSubFrom(UserId)} directly
     * @return `gis` claim value generated from the user's id
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    default String generateInternalSubFrom(User user) {
        return generateInternalSubFrom(user.getFullId());
    }

    /**
     * Fill the sub and internal sub claims based on the user profile provided as parameter
     * Note: The input JWT is mutated.
     *
     * @param jwt  jwt instance to update
     * @param user
     */
    void updateJWT(JWT jwt, User user);

    Maybe<User> findUserBySub(JWT token);

    Maybe<UserId> findUserIdBySub(JWT token);

    default Maybe<io.gravitee.am.identityprovider.api.User> getPrincipal(JWT token) {
        return findUserBySub(token)
                .map(DefaultUser::new);
    }


    boolean hasValidInternalSub(String internalSub);

    String extractUserId(String gis);

    String extractSourceId(String gis);

}
