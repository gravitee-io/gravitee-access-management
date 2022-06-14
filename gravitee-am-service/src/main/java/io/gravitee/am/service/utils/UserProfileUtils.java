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
package io.gravitee.am.service.utils;

import com.google.common.base.Strings;
import io.gravitee.am.model.User;

import java.util.Objects;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserProfileUtils {
    public static String buildDisplayName(User user) {
        String result = null;
        if (user != null && !Strings.isNullOrEmpty(user.getFirstName())) {
            result = user.getFirstName() + (user.getLastName() != null ? " " + user.getLastName() : "");
        }
        return result;
    }

    /**
     * Determine if the user displayName has been processed by AM during the User Creation.
     * if so the displayName will be updated otherwise it will remain unchanged (SCIM user may be created/updated using custom displayName)
     *
     * @param user
     * @return
     */
    public static boolean hasGeneratedDisplayName(User user) {
        return Objects.equals(user.getDisplayName(), buildDisplayName(user));
    }
}
