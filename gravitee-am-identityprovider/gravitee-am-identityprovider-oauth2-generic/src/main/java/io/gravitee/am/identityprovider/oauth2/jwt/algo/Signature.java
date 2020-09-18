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
package io.gravitee.am.identityprovider.oauth2.jwt.algo;

import io.gravitee.am.common.jwt.SignatureAlgorithm;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum Signature {
    RSA_RS256(SignatureAlgorithm.RS256),
    RSA_RS384(SignatureAlgorithm.RS384),
    RSA_RS512(SignatureAlgorithm.RS512),
    HMAC_HS256(SignatureAlgorithm.HS256),
    HMAC_HS384(SignatureAlgorithm.HS384),
    HMAC_HS512(SignatureAlgorithm.HS512);

    private SignatureAlgorithm alg;

    Signature(SignatureAlgorithm alg) {
        this.alg = alg;
    }

    public SignatureAlgorithm getAlg() {
        return alg;
    }
}
