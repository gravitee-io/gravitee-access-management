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
 * OAuth 2.0 Client Types
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-2.1">2.1. Client Types</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ClientType {

    /**
     * confidential
     *       Clients capable of maintaining the confidentiality of their
     *       credentials (e.g., client implemented on a secure server with
     *       restricted access to the client credentials), or capable of secure
     *       client authentication using other means.
     */
    String CONFIDENTIAL = "confidential";

    /**
     * public
     *       Clients incapable of maintaining the confidentiality of their
     *       credentials (e.g., clients executing on the device used by the
     *       resource owner, such as an installed native application or a web
     *       browser-based application), and incapable of secure client
     *       authentication via any other means.
     */
    String PUBLIC = "public";
}
