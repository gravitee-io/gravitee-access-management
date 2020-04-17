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
package io.gravitee.am.gateway.handler.common.jwt;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Single;


/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public interface JWTService {

    /**
     * Encode raw JWT to JWT signed string representation
     * @param jwt JWT to encode
     * @param certificateProvider certificate provider used to sign the token
     * @return JWT signed string representation
     */
    Single<String> encode(JWT jwt, CertificateProvider certificateProvider);

    /**
     * Encode raw JWT to JWT signed string representation
     * @param jwt JWT to encode
     * @param client client which want to sign the token
     * @return JWT signed string representation
     */
    Single<String> encode(JWT jwt, Client client);

    /**
     * Encode raw JWT to JWT signed representation using userinfo_signed_response_alg Client preferences.
     * @param jwt JWT to encode
     * @param client client which want to sign the token
     * @return JWT signed string representation
     */
    Single<String> encodeUserinfo(JWT jwt, Client client);

    /**
     * Encode raw JWT to JWT signed representation using authorization_signed_response_alg Client preferences.
     * @param jwt JWT to encode
     * @param client client which want to sign the token
     * @return JWT signed string representation
     */
    Single<String> encodeAuthorization(JWT jwt, Client client);

    /**
     * Decode JWT signed string representation to JWT
     * @param jwt JWT to decode
     * @param client client which want to decode the token
     * @return JWT object
     */
    Single<JWT> decodeAndVerify(String jwt, Client client);

    /**
     * Decode JWT signed string representation to JWT without signature verification
     * @param jwt JWT to decode
     * @return JWT object
     */
    Single<JWT> decode(String jwt);
}
