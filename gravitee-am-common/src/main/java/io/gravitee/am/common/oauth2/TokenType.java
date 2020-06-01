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
package io.gravitee.am.common.oauth2;

/**
 * Token-Type definition as first mentioned in <a href="https://tools.ietf.org/html/rfc6749#appendix-A.13>Oauth2</a> specification.
 * And later in <a href="https://tools.ietf.org/html/rfc8693#section-3">Token Exchange</a> specification.
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public interface TokenType {

    String ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";
    String REFRESH_TOKEN = "urn:ietf:params:oauth:token-type:refresh_token";
    String ID_TOKEN = "urn:ietf:params:oauth:token-type:id_token";
    String JWT = "urn:ietf:params:oauth:token-type:jwt";
    String SAML_1 = "urn:ietf:params:oauth:token-type:saml1";
    String SAML_2 = "urn:ietf:params:oauth:token-type:saml2";
}
