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
package io.gravitee.am.certificate.javakeystore;

import com.nimbusds.jose.JWSAlgorithm;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.AlgorithmId;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum Signature {

    SHA512withRSA("SHA-512", AlgorithmId.SHA512_oid, JWSAlgorithm.RS512),
    SHA384withRSA("SHA-384", AlgorithmId.SHA384_oid, JWSAlgorithm.RS384),
    SHA256withRSA("SHA-256", AlgorithmId.SHA256_oid, JWSAlgorithm.RS256),
    SHA224withRSA("SHA-224", AlgorithmId.SHA224_oid, null),
    SHA1withRSA("SHA-1", AlgorithmId.SHA_oid, null);

    private String digestOID;
    private ObjectIdentifier algorithmId;
    private JWSAlgorithm jwsAlgorithm;


    Signature(String digestOID, ObjectIdentifier algorithmId, JWSAlgorithm jwsAlgorithm) {
        this.digestOID = digestOID;
        this.algorithmId = algorithmId;
        this.jwsAlgorithm = jwsAlgorithm;
    }

    public String getDigestOID() {
        return digestOID;
    }

    public ObjectIdentifier getAlgorithmId() {
        return algorithmId;
    }

    public JWSAlgorithm getJwsAlgorithm() {
        return jwsAlgorithm;
    }
}
