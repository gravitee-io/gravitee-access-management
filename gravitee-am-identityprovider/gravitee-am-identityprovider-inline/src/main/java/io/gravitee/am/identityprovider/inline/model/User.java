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
package io.gravitee.am.identityprovider.inline.model;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class User {

    private static final String FIRSTNAME_ATTRIBUTE = "firstname";
    private static final String LASTNAME_ATTRIBUTE = "lastname";
    private static final String USERNAME_ATTRIBUTE = "username";
    private static final String EMAIL_ATTRIBUTE = "email";

    private String firstname;
    private String lastname;
    private String username;
    private String password;
    private String email;
    private String [] roles;

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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String[] getRoles() {
        return roles;
    }

    public void setRoles(String[] roles) {
        this.roles = roles;
    }

    public Object getAttributeValue(String attributeKey) {
        return switch (attributeKey) {
            case FIRSTNAME_ATTRIBUTE -> getFirstname();
            case LASTNAME_ATTRIBUTE -> getLastname();
            case USERNAME_ATTRIBUTE -> getUsername();
            case EMAIL_ATTRIBUTE -> getEmail();
            default -> null;
        };
    }

    public Map<String, Object> toMap() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(FIRSTNAME_ATTRIBUTE, getFirstname());
        attributes.put(LASTNAME_ATTRIBUTE, getLastname());
        attributes.put(USERNAME_ATTRIBUTE, getUsername());
        attributes.put(EMAIL_ATTRIBUTE, getEmail());
        return attributes;
    }
}
