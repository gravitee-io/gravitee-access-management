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
package io.gravitee.am.gateway.handler.common.auth;

import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationDetails {

    private Authentication principal;
    private User user;
    private Domain domain;
    private Client client;
    private Throwable throwable;
    private Map<String, Object> details;

    public AuthenticationDetails() { }

    public AuthenticationDetails(Authentication principal, Domain domain, Client client, User user) {
        this.principal = principal;
        this.domain = domain;
        this.client = client;
        this.user = user;
    }

    public AuthenticationDetails(Authentication principal, Domain domain, Client client, Throwable throwable) {
        this.principal = principal;
        this.domain = domain;
        this.client = client;
        this.throwable = throwable;
    }

    public Authentication getPrincipal() {
        return principal;
    }

    public void setPrincipal(Authentication principal) {
        this.principal = principal;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Domain getDomain() {
        return domain;
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
