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
package io.gravitee.am.gateway.handler.oidc.model.jwk.converter;

import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.KeyType;
import io.gravitee.am.model.jose.OCTKey;
import io.gravitee.am.model.jose.OKPKey;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import java.util.stream.Collectors;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JWKConverter {

    public static io.gravitee.am.gateway.handler.oidc.model.jwk.JWKSet convert(JWKSet jwkSet) {
        if (jwkSet == null) {
            return null;
        }

        io.gravitee.am.gateway.handler.oidc.model.jwk.JWKSet result = new io.gravitee.am.gateway.handler.oidc.model.jwk.JWKSet();
        result.setKeys(jwkSet.getKeys().stream().map(JWKConverter::convert).collect(Collectors.toList()));
        return result;
    }

    public static io.gravitee.am.gateway.handler.oidc.model.jwk.JWK convert(io.gravitee.am.model.jose.JWK jwk) {
        switch (KeyType.parse(jwk.getKty())) {
            case EC:
                return io.gravitee.am.gateway.handler.oidc.model.jwk.ECKey.from((ECKey) jwk);
            case RSA:
                return io.gravitee.am.gateway.handler.oidc.model.jwk.RSAKey.from((RSAKey) jwk);
            case OCT:
                return io.gravitee.am.gateway.handler.oidc.model.jwk.OCTKey.from((OCTKey) jwk);
            case OKP:
                return io.gravitee.am.gateway.handler.oidc.model.jwk.OKPKey.from((OKPKey) jwk);
            default:
                return null;
        }
    }
}
