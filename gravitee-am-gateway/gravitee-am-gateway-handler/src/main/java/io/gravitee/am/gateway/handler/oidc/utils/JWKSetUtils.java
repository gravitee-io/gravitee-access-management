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
package io.gravitee.am.gateway.handler.oidc.utils;

import io.gravitee.am.gateway.handler.oidc.jwk.ECKey;
import io.gravitee.am.gateway.handler.oidc.jwk.JWK;
import io.gravitee.am.gateway.handler.oidc.jwk.JWKSet;
import io.gravitee.am.gateway.handler.oidc.jwk.RSAKey;
import io.gravitee.am.model.jose.KeyType;

import java.util.stream.Collectors;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JWKSetUtils {

    public static JWKSet convert(io.gravitee.am.model.oidc.JWKSet jwkSet) {
        if(jwkSet==null) {
            return null;
        }

        JWKSet result = new JWKSet();
        result.setKeys(jwkSet.getKeys()
                .stream()
                .map(JWKSetUtils::convert)
                .collect(Collectors.toList())
        );
        return result;
    }

    public static JWK convert(io.gravitee.am.model.jose.JWK jwk) {
        switch (KeyType.valueOf(jwk.getKty())) {
            case EC:return ECKey.from((io.gravitee.am.model.jose.ECKey)jwk);
            case RSA:return RSAKey.from((io.gravitee.am.model.jose.RSAKey)jwk);
            case OCT:return null;
            case OKP:return null;
            default:return null;
        }
    }
}
