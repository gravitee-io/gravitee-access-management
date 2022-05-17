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

package io.gravitee.am.service.impl.user.activity.utils;

import io.gravitee.am.service.impl.user.activity.configuration.UserActivityConfiguration.Algorithm;
import java.nio.charset.StandardCharsets;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static org.apache.commons.codec.digest.Sha2Crypt.sha256Crypt;
import static org.apache.commons.codec.digest.Sha2Crypt.sha512Crypt;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HashedKeyUtils {

    static final String SHA256_PREFIX = "$5$";

    static final String SHA512_PREFIX = "$6$";

    public static String computeHash(Algorithm algorithm, String userId, String salt) {
        if (isNull(algorithm) || isNull(userId)) {
            return null;
        }
        final byte[] userIdBytes = userId.getBytes(StandardCharsets.UTF_8);
        return getHash(algorithm, userId, salt, userIdBytes);
    }

    private static String getHash(Algorithm algorithm, String userId, String salt, byte[] userIdBytes) {
        switch (algorithm) {
            case SHA256:
                return sha256Crypt(userIdBytes, ofNullable(salt).map(s -> SHA256_PREFIX + s).orElse(null));
            case SHA512:
                return sha512Crypt(userIdBytes, ofNullable(salt).map(s -> SHA512_PREFIX + s).orElse(null));
            default:
                return userId;
        }
    }
}
