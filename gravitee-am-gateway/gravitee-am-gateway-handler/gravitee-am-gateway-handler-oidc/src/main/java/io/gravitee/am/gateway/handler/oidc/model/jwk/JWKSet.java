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
package io.gravitee.am.gateway.handler.oidc.model.jwk;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * See <a href="https://tools.ietf.org/html/rfc7517#section-5">5. JWK Set Format</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JWKSet {

    @JsonProperty("keys")
    private List<JWK> keys;

    public List<JWK> getKeys() {
        return keys;
    }

    public void setKeys(List<JWK> keys) {
        this.keys = keys;
    }
}
