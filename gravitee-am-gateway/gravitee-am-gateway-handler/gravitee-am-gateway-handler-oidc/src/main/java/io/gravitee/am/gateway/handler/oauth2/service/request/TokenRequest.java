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
package io.gravitee.am.gateway.handler.oauth2.service.request;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import lombok.Getter;
import lombok.Setter;

/**
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.1.3">Access Token Request</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */

@Getter
@Setter
public class TokenRequest extends OAuth2Request {

    /**
     * The resource owner username.
     * REQUIRED for <a href="https://tools.ietf.org/html/rfc6749#section-4.3">Resource Owner Password Credentials Grant</a>
     */
    private String username;

    /**
     * The resource owner password.
     * REQUIRED for <a href="https://tools.ietf.org/html/rfc6749#section-4.3">Resource Owner Password Credentials Grant</a>
     */
    private String password;

    /**
     * REQUIRED for <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#uma-grant-type">User Managed Access Grant</a>
     */
    private String ticket;

    /**
     * REQUIRED for <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#uma-grant-type">User Managed Access Grant</a>
     */
    private String claimToken;

    /**
     * REQUIRED for <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#uma-grant-type">User Managed Access Grant</a>
     */
    private String claimTokenFormat;

    /**
     * REQUIRED for <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#uma-grant-type">User Managed Access Grant</a>
     */
    private String persistedClaimsToken;

    /**
     * REQUIRED for <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#uma-grant-type">User Managed Access Grant</a>
     */
    private String requestingPartyToken;

}
