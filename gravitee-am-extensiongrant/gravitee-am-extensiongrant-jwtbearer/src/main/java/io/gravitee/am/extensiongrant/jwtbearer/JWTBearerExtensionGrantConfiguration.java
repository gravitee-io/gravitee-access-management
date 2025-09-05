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
package io.gravitee.am.extensiongrant.jwtbearer;

import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.extensiongrant.api.ExtensionGrantConfiguration;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class JWTBearerExtensionGrantConfiguration implements ExtensionGrantConfiguration {

    private KeyResolver publicKeyResolver;
    private Signature signature;
    private String publicKey;
    private List<Map<String, String>> claimsMapper;

    public enum KeyResolver {
        GIVEN_KEY,
        JWKS_URL
    }
    public enum Signature {
        RS256(SignatureAlgorithm.RS256),
        RS384(SignatureAlgorithm.RS384),
        RS512(SignatureAlgorithm.RS512),
        HS256(SignatureAlgorithm.HS256),
        HS384(SignatureAlgorithm.HS384),
        HS512(SignatureAlgorithm.HS512),
        ES256(SignatureAlgorithm.ES256),
        ES384(SignatureAlgorithm.ES384),
        ES512(SignatureAlgorithm.ES512);

        private SignatureAlgorithm alg;

        Signature(SignatureAlgorithm alg) {
            this.alg = alg;
        }

        public SignatureAlgorithm getAlg() {
            return alg;
        }
    }
}


