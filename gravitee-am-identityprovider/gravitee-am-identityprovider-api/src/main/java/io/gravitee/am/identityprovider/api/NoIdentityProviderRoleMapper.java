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
package io.gravitee.am.identityprovider.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class NoIdentityProviderRoleMapper implements IdentityProviderRoleMapper {
    @Override
    public Map<String, String[]> getRoles() {
        return null;
    }

    @Override
    public void setRoles(Map<String, String[]> roles) {
        // nothing to do
    }

    @Override
    public List<String> apply(AuthenticationContext context, Map<String, Object> userInfo) {
        return emptyList();
    }
}
