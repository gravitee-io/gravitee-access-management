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
package io.gravitee.am.common.jwt;

import lombok.Getter;

/**
 * Type-safe representation of the {@code typ} header values AM signs with, as defined by
 * <a href="https://tools.ietf.org/html/rfc7519#section-5.1">RFC 7519</a> and recommended by
 * <a href="https://tools.ietf.org/html/rfc8725#section-3.11">JSON Web Token Best Current Practices</a>.
 *
 * @author GraviteeSource Team
 */
@Getter
public enum JwtType {

    /**
     * Identity assertion JWT authorization grant, as defined in
     * <a href="https://datatracker.ietf.org/doc/html/draft-ietf-oauth-identity-assertion-authz-grant-04#section-3.1">draft-ietf-oauth-identity-assertion-authz-grant</a>
     */
    ID_JAG("oauth-id-jag+jwt");

    private final String value;

    JwtType(String value) {
        this.value = value;
    }
}
