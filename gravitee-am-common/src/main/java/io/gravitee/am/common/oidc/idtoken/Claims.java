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
package io.gravitee.am.common.oidc.idtoken;

/**
 * ID Token Claims
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#IDToken">2. ID Token</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface Claims extends io.gravitee.am.common.jwt.Claims {

    /**
     * Time when the End-User authentication occurred. Its value is a JSON number representing the number of seconds from 1970-01-01T0:0:0Z as measured in UTC until the date/time.
     * When a max_age request is made or when auth_time is requested as an Essential Claim, then this Claim is REQUIRED; otherwise, its inclusion is OPTIONAL.
     * (The auth_time Claim semantically corresponds to the OpenID 2.0 PAPE [OpenID.PAPE] auth_time response parameter.)
     */
    String auth_time = "auth_time";

    /**
     * String value used to associate a Client session with an ID Token, and to mitigate replay attacks.
     * The value is passed through unmodified from the Authentication Request to the ID Token.
     * If present in the ID Token, Clients MUST verify that the nonce Claim Value is equal to the value of the nonce parameter sent in the Authentication Request.
     * If present in the Authentication Request, Authorization Servers MUST include a nonce Claim in the ID Token with the Claim Value being the nonce value sent in the Authentication Request.
     * Authorization Servers SHOULD perform no other processing on nonce values used. The nonce value is a case sensitive string.
     */
    String nonce = "nonce";

    /**
     * OPTIONAL. Authentication Context Class Reference. String specifying an Authentication Context Class Reference value that identifies the Authentication Context Class that the authentication performed satisfied.
     * The value "0" indicates the End-User authentication did not meet the requirements of ISO/IEC 29115 [ISO29115] level 1.
     * Authentication using a long-lived browser cookie, for instance, is one example where the use of "level 0" is appropriate.
     * Authentications with level 0 SHOULD NOT be used to authorize access to any resource of any monetary value.
     * (This corresponds to the OpenID 2.0 PAPE [OpenID.PAPE] nist_auth_level 0.) An absolute URI or an RFC 6711 [RFC6711] registered name SHOULD be used as the acr value; registered names MUST NOT be used with a different meaning than that which is registered.
     * Parties using this claim will need to agree upon the meanings of the values used, which may be context-specific. The acr value is a case sensitive string.
     */
    String acr = "acr";

    /**
     * OPTIONAL. Authentication Methods References. JSON array of strings that are identifiers for authentication methods used in the authentication.
     * For instance, values might indicate that both password and OTP authentication methods were used.
     * The definition of particular values to be used in the amr Claim is beyond the scope of this specification.
     * Parties using this claim will need to agree upon the meanings of the values used, which may be context-specific.
     * The amr value is an array of case sensitive strings.
     */
    String amr = "amr";

    /**
     * OPTIONAL. Authorized party - the party to which the ID Token was issued.
     * If present, it MUST contain the OAuth 2.0 Client ID of this party.
     * This Claim is only needed when the ID Token has a single audience value and that audience is different than the authorized party.
     * It MAY be included even when the authorized party is the same as the sole audience.
     * The azp value is a case sensitive string containing a StringOrURI value.
     */
    String azp = "azp";

    /**
     * Access Token hash value.
     * Its value is the base64url encoding of the left-most half of the hash of the octets of the ASCII representation of the access_token value,
     * where the hash algorithm used is the hash algorithm used in the alg Header Parameter of the ID Token's JOSE Header.
     * For instance, if the alg is RS256, hash the access_token value with SHA-256, then take the left-most 128 bits and base64url encode them.
     * The at_hash value is a case sensitive string.
     * If the ID Token is issued from the Authorization Endpoint with an access_token value,
     * which is the case for the response_type value code id_token token, this is REQUIRED; otherwise, its inclusion is OPTIONAL.
     */
    String at_hash = "at_hash";

    /**
     * Code hash value.
     * Its value is the base64url encoding of the left-most half of the hash of the octets of the ASCII representation of the code value,
     * where the hash algorithm used is the hash algorithm used in the alg Header Parameter of the ID Token's JOSE Header.
     * For instance, if the alg is HS512, hash the code value with SHA-512, then take the left-most 256 bits and base64url encode them.
     * The c_hash value is a case sensitive string.
     * If the ID Token is issued from the Authorization Endpoint with a code, which is the case for the response_type values code id_token and code id_token token, this is REQUIRED; otherwise, its inclusion is OPTIONAL.
     */
    String c_hash = "c_hash";

    /**
     * State hash value.
     * Its value is the base64url encoding of the left-most half of the hash of the octets of the ASCII representation
     * of the state value, where the hash algorithm used is the hash algorithm used in the alg header parameter of the
     * ID Token's JOSE header. For instance, if the alg is HS512, hash the state value with SHA-512, then take the
     * left-most 256 bits and base64url encode them. The s_hash value is a case sensitive string.
     */
    String s_hash = "s_hash";
}
