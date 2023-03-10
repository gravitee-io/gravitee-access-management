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

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * @author Aurélien PACAUD (aurelien.pacaud at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class MovingFactorUtils {

    public static final String SHA_1_PRNG = "SHA1PRNG";

    private MovingFactorUtils() {

    }

    public static int generateInitialMovingFactor(String seed) {
        try {
            SecureRandom secureRandom = SecureRandom.getInstance(SHA_1_PRNG);
            secureRandom.setSeed(seed.getBytes(StandardCharsets.UTF_8));
            return secureRandom.nextInt(1000) + 1;
        } catch (NoSuchAlgorithmException e) {
            return 0;
        }
    }
}
