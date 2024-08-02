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
import io.reactivex.rxjava3.core.Maybe;

public interface SubjectManager {

    String generateSubFrom(User user);

    String generateInternalSubFrom(User user);

    /**
     * Fill the sub and internal sub claims based on the user profile provided as parameter
     * Note: The input JWT is mutated.
     *
     * @param jwt jwt instance to update
     * @param user
     * @return the input JWT updated with additional claims
     */
    void updateJWT(JWT jwt, User user);

    Maybe<User> findUserBySub(JWT token);

    Maybe<String> findUserIdBySub(JWT token);

    default Maybe<io.gravitee.am.identityprovider.api.User> getPrincipal(JWT token) {
        return findUserBySub(token)
                .map(DefaultUser::new);
    }

    String extractUserId(String gis);
}
