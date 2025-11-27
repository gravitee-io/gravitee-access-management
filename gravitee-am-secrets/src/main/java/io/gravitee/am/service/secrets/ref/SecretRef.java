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
package io.gravitee.am.service.secrets.ref;

import com.google.common.collect.Multimap;
import io.gravitee.secrets.api.core.SecretURL;

/**
 * @author GraviteeSource Team
 */
public record SecretRef(
        String provider,
        String path,
        String key,
        Multimap<String, String> parameters
) {
    private static final boolean IS_URI = true;

    public SecretRef useExplicitKey(String explicitKey) {
        if (key != null) {
            throw new IllegalArgumentException("Key already defined in SecretRef path; cannot also provide an explicit key");
        }

        return new SecretRef(provider, path, explicitKey, parameters);
    }

    public SecretURL toSecretURL() {
        return new SecretURL(provider, path, key, parameters, IS_URI);
    }
}
