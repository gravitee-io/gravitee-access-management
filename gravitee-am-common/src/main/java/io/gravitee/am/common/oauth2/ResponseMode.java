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
 * OAuth2 Response Modes
 *
 * See <a href="https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html#ResponseModes">OAuth 2.0 Response Modes</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ResponseMode {

    /**
     * In this mode, Authorization Response parameters are encoded in the query string added to the redirect_uri when
     * redirecting back to the Client.
     */
    String QUERY = "query";

    /**
     * In this mode, Authorization Response parameters are encoded in the fragment added to the redirect_uri when
     * redirecting back to the Client.
     */
    String FRAGMENT = "fragment";
}
