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

import java.util.Arrays;
import java.util.List;

/**
 * Custom OpenID Connect Claims registered by Gravitee.io AM
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CustomClaims {
    /**
     * End-User's groups.
     */
    String GROUPS = "groups";
    /**
     * End-User's roles.
     */
    String ROLES = "roles";

    static List<String> claims() {
        return Arrays.asList(GROUPS, ROLES);
    }
}
