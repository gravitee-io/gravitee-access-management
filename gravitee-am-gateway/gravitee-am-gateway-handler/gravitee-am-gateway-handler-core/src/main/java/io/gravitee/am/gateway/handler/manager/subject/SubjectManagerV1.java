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
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
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
public class SubjectManagerV1 implements SubjectManager {

    private UserService userService;

    @Override
    public String generateSubFrom(User user) {
        return generateInternalSubFrom(user);
    }

    @Override
    public String generateInternalSubFrom(User user) {
        return user.getId();
    }

    @Override
    public void updateJWT(JWT jwt, User user) {
        // no need to provide internal sub claim for domain V1
        // we are deciding some actions based of the presence of
        // this claim.
        jwt.setSub(generateSubFrom(user));
    }

    @Override
    public Maybe<User> findUserBySub(JWT token) {
        return this.userService.findById(token.getSub());
    }

    @Override
    public Maybe<String> findUserIdBySub(JWT token) {
        return Maybe.just(token.getSub());
    }

    @Override
    public Maybe<io.gravitee.am.identityprovider.api.User> getPrincipal(JWT token) {
        return findUserBySub(token)
                .map(DefaultUser::new);
    }

    @Override
    public String extractUserId(String gis) {
        return gis;
    }
}
