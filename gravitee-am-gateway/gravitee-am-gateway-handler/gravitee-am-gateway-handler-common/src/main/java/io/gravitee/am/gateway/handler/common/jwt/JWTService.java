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

import io.gravitee.am.common.jwt.EncodedJWT;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;

import java.util.function.Supplier;


/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public interface JWTService {

    enum TokenType {
        ACCESS_TOKEN,
        ID_TOKEN,
        REFRESH_TOKEN,
        SESSION,
        JARM,
        STATE
    }

    /**
     * Encode raw JWT to JWT signed string representation
     * @param jwt JWT to encode
     * @param certificateProvider certificate provider used to sign the token
     * @return JWT signed string representation together with certificate info
     */
    Single<EncodedJWT> encodeJwt(JWT jwt, CertificateProvider certificateProvider);

    /**
     * Encode raw JWT to JWT signed string representation
     * @param jwt JWT to encode
     * @param certificateProvider certificate provider used to sign the token
     * @return JWT signed string representation
     */
    default Single<String> encode(JWT jwt, CertificateProvider certificateProvider) {
        return encodeJwt(jwt, certificateProvider).map(EncodedJWT::encodedToken);
    }

    /**
     * Encode raw JWT to JWT signed string representation
     * @param jwt JWT to encode
     * @param client client which want to sign the token
     * @return JWT signed string representation together with certificate info
     */
    Single<EncodedJWT> encodeJwt(JWT jwt, Client client);

    /**
     * Encode raw JWT to JWT signed string representation
     * @param jwt JWT to encode
     * @param client client which want to sign the token
     * @return JWT signed string representation
     */
    default Single<String> encode(JWT jwt, Client client) {
        return encodeJwt(jwt, client).map(EncodedJWT::encodedToken);
    }

    /**
     * Encode raw JWT to JWT signed representation using userinfo_signed_response_alg Client preferences.
     * @param jwt JWT to encode
     * @param client client which want to sign the token
     * @return JWT signed string representation together with certificate info
     */
    Single<EncodedJWT> encodeUserinfo(JWT jwt, Client client);

    /**
     * Encode raw JWT to JWT signed representation using authorization_signed_response_alg Client preferences.
     * @param jwt JWT to encode
     * @param client client which want to sign the token
     * @return JWT signed string representation together with certificate info
     */
    Single<EncodedJWT> encodeAuthorization(JWT jwt, Client client);

    /**
     * Decode JWT signed string representation to JWT using certificate from hint claim or client
     * @param jwt JWT to decode
     * @param getDefaultCertificateId supplier of ID of certificate to use to decode the token if <code>kid</code> header parameter is not set
     * @return JWT object
     */
    Single<JWT> decodeAndVerify(String jwt, Supplier<String> getDefaultCertificateId, TokenType tokenType);

    /**
     * Decode JWT signed string representation to JWT
     * @param jwt JWT to decode
     * @param client client which want to decode the token
     * @return JWT object
     */
    default Single<JWT> decodeAndVerify(String jwt, Client client, TokenType tokenType) {
        return decodeAndVerify(jwt, client::getCertificate, tokenType);
    }

    /**
     * Decode JWT signed string representation to JWT using the specified certificate provider.
     * @param jwt JWT to decode
     * @param certificateProvider the certificate provider to use to verify jwt signature.
     * @param tokenType type of the decoded token
     * @return JWT object
     */
    Single<JWT> decodeAndVerify(String jwt, CertificateProvider certificateProvider, TokenType tokenType);

    /**
     * Decode JWT signed string representation to JWT without signature verification
     * @param jwt JWT to decode
     * @param tokenType the type of token to decode
     * @return JWT object
     */
    Single<JWT> decode(String jwt, TokenType tokenType);
}
