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
package io.gravitee.am.common.oidc;

/**
 * OpenID Connect Claim Types
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#ClaimTypes">5.6. Claim Types</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ClaimType {

    /**
     * Claims that are directly asserted by the OpenID Provider.
     */
    String NORMAL = "normal";

    /**
     * Claims that are asserted by a Claims Provider other than the OpenID Provider but are returned by OpenID Provider.
     */
    String AGGREGATED = "aggregated";

    /**
     * Claims that are asserted by a Claims Provider other than the OpenID Provider but are returned as references by the OpenID Provider.
     */
    String DISTRIBUTED = "distributed";
}
