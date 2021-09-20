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
package io.gravitee.am.gateway.handler.oidc.service.jwe;

import com.nimbusds.jwt.JWT;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Single;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public interface JWEService {

    /**
     * Encode raw JWT to JWT signed representation using id_token_encrypted_response_alg Client preferences.
     * @param signedJwt Signed JWT to encrypt
     * @param client client which want to encrypt the token
     * @return JWT encrypted string representation
     */
    Single<String> encryptIdToken(String signedJwt, Client client);

    /**
     * Encode raw JWT to JWT signed representation using userinfo_encrypted_response_alg Client preferences.
     * @param signedJwt Signed JWT to encrypt
     * @param client client which want to encrypt the token
     * @return JWT encrypted string representation
     */
    Single<String> encryptUserinfo(String signedJwt, Client client);

    Single<JWT> decrypt(String jwt, Client client);

    /**
     * Decrypt JWT send by RP.
     * This decryption action will use a private key provided by the domain jwks
     *
     * @param jwt
     * @return
     */
    Single<JWT> decrypt(String jwt);

    /**
     * Encode raw JWT to JWT signed representation using authorization_encrypted_response_alg Client preferences.
     * @param signedJwt Signed JWT to encrypt
     * @param client client which want to encrypt the token
     * @return JWT encrypted string representation
     */
    Single<String> encryptAuthorization(String signedJwt, Client client);
}
