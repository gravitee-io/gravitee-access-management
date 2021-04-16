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
package io.gravitee.am.common.oauth2;

/**
 *
 * See <a href="https://tools.ietf.org/html/rfc7009#section-2.1"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum TokenTypeHint {
    ACCESS_TOKEN,
    REFRESH_TOKEN,
    ID_TOKEN;

    public static TokenTypeHint from(String name) throws IllegalArgumentException {
        return TokenTypeHint.valueOf(name.toUpperCase());
    }
}
