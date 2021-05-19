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

import io.gravitee.am.identityprovider.api.IdentityProviderRoleMapper;
import io.gravitee.el.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultIdentityProviderRoleMapper implements IdentityProviderRoleMapper {
    public static final Logger LOGGER = LoggerFactory.getLogger(DefaultIdentityProviderRoleMapper.class);

    public static final String CONTEXT_KEY_PROFILE = "profile";
    private Map<String, String[]> roles;

    public Map<String, String[]> getRoles() {
        return this.roles;
    }

    public void setRoles(Map<String, String[]> roles) {
        this.roles = roles;
    }

    public List<String> apply(AuthenticationContext context, Map<String, Object> userInfo) {
        Set<String> mappedRoles = new HashSet<>();
        if (this.getRoles() != null) {

            TemplateEngine templateEngine = context.getTemplateEngine();
            if (templateEngine != null) {
                templateEngine.getTemplateContext().setVariable(CONTEXT_KEY_PROFILE, userInfo);
            }

            this.getRoles().forEach((role, users) -> {
                Arrays.asList(users).stream().filter(u -> !StringUtils.isEmpty(u)).map(String::trim).forEach(u -> {
                    if (u.startsWith("{") && u.endsWith("}") && templateEngine != null) {
                        try {
                            if (templateEngine.getValue(u, Boolean.class)) {
                                mappedRoles.add(role);
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Role mapper can't evaluate the expression [{}] as boolean", u);
                        }
                    } else {
                        // user/group have the following syntax userAttribute=userValue
                        String[] attributes = u.split("=", 2);
                        String userAttribute = attributes[0];
                        String userValue = attributes[1];
                        if (userInfo.containsKey(userAttribute)) {
                            if (userInfo.get(userAttribute) instanceof Collection && ((Collection<?>) userInfo.get(userAttribute)).contains(userValue)) {
                                mappedRoles.add(role);
                            } else if (userValue.equals(userInfo.get(userAttribute))) {
                                mappedRoles.add(role);
                            }
                        }
                    }
                });
            });
        }
        return new ArrayList<>(mappedRoles);
    }
}
