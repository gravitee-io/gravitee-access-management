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
package io.gravitee.am.model.oidc;

import io.gravitee.am.model.jose.JWK;
import java.util.ArrayList;
import java.util.List;

public class JWKSet implements Cloneable {

    private List<JWK> keys;

    public List<JWK> getKeys() {
        return keys;
    }

    public void setKeys(List<JWK> keys) {
        this.keys = keys;
    }

    @Override
    public JWKSet clone() throws CloneNotSupportedException {
        JWKSet clone = (JWKSet) super.clone();
        clone.setKeys(this.getKeys() != null ? new ArrayList<>(this.getKeys()) : null);
        return clone;
    }
}
