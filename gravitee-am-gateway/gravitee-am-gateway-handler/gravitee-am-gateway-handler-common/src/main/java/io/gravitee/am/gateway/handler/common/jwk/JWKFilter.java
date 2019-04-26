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
package io.gravitee.am.gateway.handler.common.jwk;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.util.Base64URL;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.KeyType;

import java.math.BigInteger;
import java.util.function.Predicate;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JWKFilter {
    /**
     * @return Filter to retrieve non weak (>=2048bits) RSA keys expected use for encryption.
     */
    public static Predicate<JWK> RSA_KEY_ENCRYPTION() {
        return jwk -> jwk !=null && KeyType.RSA.getKeyType().equals(jwk.getKty()) &&
                ((io.gravitee.am.model.jose.RSAKey)jwk).getN()!=null && //Filter weak keys
                new BigInteger(((io.gravitee.am.model.jose.RSAKey)jwk).getN().getBytes()).bitLength()>=2048 && //Filter weak keys
                (KeyUse.ENCRYPTION.getValue().equals(jwk.getUse()) || jwk.getUse()==null);
    }

    /**
     * @return Filter to Shared Secret keys (OCT).
     */
    public static Predicate<io.gravitee.am.model.jose.JWK> OCT_KEY_ENCRYPTION() {
        return jwk -> jwk !=null && KeyType.OCT.getKeyType().equals(jwk.getKty()) &&
                (KeyUse.ENCRYPTION.getValue().equals(jwk.getUse()) || jwk.getUse()==null);
    }

    /**
     * @return Filter to retrieve AES keys, with same size as the algorithm, expected use for encryption.
     */
    public static Predicate<io.gravitee.am.model.jose.JWK> OCT_KEY_ENCRYPTION(EncryptionMethod encryptionMethod) {
        return jwk -> jwk != null && KeyType.OCT.getKeyType().equals(jwk.getKty()) &&
                ((io.gravitee.am.model.jose.OCTKey)jwk).getK()!=null &&
                new Base64URL(((io.gravitee.am.model.jose.OCTKey)jwk).getK()).decode().length*8 == encryptionMethod.cekBitLength() &&
                (KeyUse.ENCRYPTION.getValue().equals(jwk.getUse()) || jwk.getUse() == null);
    }

    /**
     * @return Filter to retrieve AES keys, with same size as the algorithm, expected use for encryption.
     */
    public static Predicate<io.gravitee.am.model.jose.JWK> OCT_KEY_ENCRYPTION(JWEAlgorithm algorithm) {

        return jwk -> {
            int expectedKeySize;//AES require same size key/alg
            if (JWEAlgorithm.A128KW.equals(algorithm) || JWEAlgorithm.A128GCMKW.equals(algorithm)) {
                expectedKeySize = 16;//128/8
            } else if (JWEAlgorithm.A192KW.equals(algorithm) || JWEAlgorithm.A192GCMKW.equals(algorithm)) {
                expectedKeySize = 24;//192/8
            } else if (JWEAlgorithm.A256KW.equals(algorithm) || JWEAlgorithm.A256GCMKW.equals(algorithm)) {
                expectedKeySize = 32;//256/8
            } else {
                return false;
            }

            return jwk != null && KeyType.OCT.getKeyType().equals(jwk.getKty()) &&
                    ((io.gravitee.am.model.jose.OCTKey)jwk).getK()!=null &&
                    new Base64URL(((io.gravitee.am.model.jose.OCTKey)jwk).getK()).decode().length == expectedKeySize &&
                    (KeyUse.ENCRYPTION.getValue().equals(jwk.getUse()) || jwk.getUse() == null);
        };
    }

    /**
     * @return Filter to retrieve Elliptic or Edward Curve keys expected use for encryption.
     */
    public static Predicate<io.gravitee.am.model.jose.JWK> CURVE_KEY_ENCRYPTION() {
        return jwk -> jwk !=null && (KeyType.EC.getKeyType().equals(jwk.getKty()) || isOctetKeyPairEncryption(jwk)) &&
                (KeyUse.ENCRYPTION.getValue().equals(jwk.getUse()) || jwk.getUse()==null);
    }

    private static boolean isOctetKeyPairEncryption(io.gravitee.am.model.jose.JWK jwk) {
        //OKP keys curve can be Ed25519 (Signature only) or X25519 (Encryption only)
        if(jwk!=null && com.nimbusds.jose.jwk.KeyType.OKP.getValue().equals(jwk.getKty())) {
            return Curve.X25519.getName().equals(((io.gravitee.am.model.jose.OKPKey)jwk).getCrv());
        }
        return false;
    }
}
