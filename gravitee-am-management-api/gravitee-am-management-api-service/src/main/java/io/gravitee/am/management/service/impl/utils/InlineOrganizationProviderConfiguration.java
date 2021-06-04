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
package io.gravitee.am.management.service.impl.utils;

import io.gravitee.am.model.*;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.service.RoleService;
import io.reactivex.Flowable;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.*;

import static io.gravitee.am.model.permissions.DefaultRole.ORGANIZATION_OWNER;
import static io.gravitee.am.model.permissions.DefaultRole.ORGANIZATION_USER;
import static io.gravitee.am.model.permissions.SystemRole.ORGANIZATION_PRIMARY_OWNER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InlineOrganizationProviderConfiguration extends OrganizationProviderConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(InlineOrganizationProviderConfiguration.class);

    public static final String MEMORY_TYPE = "memory";

    private static final List<String> authorizedRoles = Arrays.asList(ORGANIZATION_OWNER.name(), ORGANIZATION_USER.name(), ORGANIZATION_PRIMARY_OWNER.name());

    private final String passwordEncoder;

    private final Map<String, UserDefinition> users = new LinkedHashMap<>();

    private RoleService roleService;

    public InlineOrganizationProviderConfiguration(RoleService roleService, Environment env, int index) {
        super(MEMORY_TYPE, env, index);
        this.roleService = roleService;
        final String propertyBase = this.getPropertyBase(index);
        this.passwordEncoder = env.getProperty(propertyBase+"password-encoding-algo", String.class, "BCrypt");

        boolean found = true;
        int idx = 0;

        while (found) {
            final String userPropertyBase = propertyBase + "users[" + idx + "].";
            final String username = env.getProperty(userPropertyBase + "username");
            found = (username != null);
            if (found) {
                UserDefinition user = new UserDefinition();
                user.setUsername(username);
                user.setFirstname(env.getProperty(userPropertyBase+"firstname"));
                user.setLastname(env.getProperty(userPropertyBase+"lastname"));
                user.setEmail(env.getProperty(userPropertyBase+"email"));
                user.setPassword(env.getProperty(userPropertyBase+"password"));
                user.setRole(env.getProperty(userPropertyBase+"role"));
                if (StringUtils.isEmpty(user.getPassword()) || StringUtils.isEmpty(user.getRole())) {
                    LOGGER.warn("User definition ignored for '{}': missing role or password", username);
                } else if (!authorizedRoles.contains(user.getRole())) {
                    LOGGER.warn("User definition ignored for '{}': invalid role. (expected: \"ORGANIZATION_OWNER\", \"ORGANIZATION_USER\", \"ORGANIZATION_PRIMARY_OWNER\")", username);
                } else {
                    this.users.put(username, user);
                }
            }
            idx++;
        }
    }

    @Override
    public IdentityProvider buildIdentityProvider() {
        IdentityProvider provider = new IdentityProvider();
        provider.setId("memory");

        provider.setConfiguration(generateConfiguration());

        provider.setExternal(false);
        provider.setType("inline-am-idp");// use the inline provider implementation as InMemory provider
        provider.setName(getName());
        provider.setReferenceId(Organization.DEFAULT);
        provider.setReferenceType(ReferenceType.ORGANIZATION);
        provider.setRoleMapper(generateRoleMapper());
        return provider;
    }

    private String generateConfiguration() {
        JsonObject json = new JsonObject();
        if ("BCrypt".equals(passwordEncoder)) {
            json.put("passwordEncoder", passwordEncoder);
        }
        JsonArray arrayOfUsers = new JsonArray();
        json.put("users", arrayOfUsers);
        users.forEach((username, def) -> {
            JsonObject user = new JsonObject()
                    .put("firstname", def.firstname)
                    .put("lastname", def.lastname)
                    .put("username", def.username)
                    .put("email", def.email)
                    .put("password", def.password);
            arrayOfUsers.add(user);
        });
        return json.encode();
    }

    private Map<String, String[]> generateRoleMapper() {
        Map<String, String[]> result = new HashMap<>();

        final List<String> roleNames = Arrays.asList(
                SystemRole.ORGANIZATION_PRIMARY_OWNER.name(),
                ORGANIZATION_OWNER.name(),
                ORGANIZATION_USER.name());

        final Map<String, Role> organizationRoles = Flowable.merge(
                roleService.findRolesByName(ReferenceType.PLATFORM, Platform.DEFAULT, ReferenceType.ORGANIZATION, roleNames),
                roleService.findRolesByName(ReferenceType.ORGANIZATION, Organization.DEFAULT, ReferenceType.ORGANIZATION, roleNames))
                .collect(HashMap<String, Role>::new, (acc, role) -> {
                    acc.put(role.getName(), role);
                }).blockingGet();

        users.forEach((username, def) -> {
            Role role = organizationRoles.get(def.getRole());
            if (role != null) {
                String[] rules = result.get(role.getId());
                if (rules == null) {
                    rules = new String[1];
                } else {
                    rules = Arrays.copyOf(rules, rules.length + 1);
                }
                rules[rules.length - 1] = "username=" + username;
                result.put(role.getId(), rules);
            }
        });

        return result;
    }

    public String getPasswordEncoder() {
        return passwordEncoder;
    }

    public Map<String, UserDefinition> getUsers() {
        return users;
    }

    public final static class UserDefinition {
        private String username;
        private String email;
        private String firstname;
        private String lastname;
        private String password;
        private String role;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getFirstname() {
            return firstname;
        }

        public void setFirstname(String firstname) {
            this.firstname = firstname;
        }

        public String getLastname() {
            return lastname;
        }

        public void setLastname(String lastname) {
            this.lastname = lastname;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
