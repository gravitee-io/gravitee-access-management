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
package io.gravitee.am.gateway.handler.common.utils;

import io.gravitee.am.model.User;

import java.util.Date;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author GraviteeSource Team
 */
public final class SessionValidityUtils {

    // the issue date of a cookie may be truncated to the second
    private static final long TOLERANCE_MILLIS = 1000L;

    private SessionValidityUtils() {
    }

    /**
     * A session or a remember-me cookie must not outlive an operation that invalidates the previous sign-in
     * (password reset, username reset, logout).
     *
     * @return true if one of those operations happened after the given sign-in date
     */
    public static boolean isIssuedBeforeUserInvalidation(User user, long issuedAtMillis) {
        return Stream.of(user.getLastPasswordReset(), user.getLastUsernameReset(), user.getLastLogoutAt())
                .filter(Objects::nonNull)
                .map(Date::getTime)
                .anyMatch(time -> time - issuedAtMillis > TOLERANCE_MILLIS);
    }
}
