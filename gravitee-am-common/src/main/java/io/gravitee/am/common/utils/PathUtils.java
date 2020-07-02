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
package io.gravitee.am.common.utils;

import java.util.regex.Pattern;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PathUtils {

    private static final Pattern DUPLICATE_SLASH_REMOVER = Pattern.compile("[//]+");

    public static String sanitize(String path) {

        if (path == null || "".equals(path)) {
            return "/";
        }

        path = DUPLICATE_SLASH_REMOVER.matcher(path).replaceAll("/");

        StringBuilder pathBuilder = new StringBuilder();

        if (!path.startsWith("/")) {
            pathBuilder.append("/");
        }

        if (path.endsWith("/") && path.length() > 1) {
            pathBuilder.append(path, 0, path.length() - 1);
        } else {
            pathBuilder.append(path);
        }

        return pathBuilder.toString();
    }
}
