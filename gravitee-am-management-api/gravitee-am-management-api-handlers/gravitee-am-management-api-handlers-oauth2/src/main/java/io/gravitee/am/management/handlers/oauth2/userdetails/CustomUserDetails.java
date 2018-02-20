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
package io.gravitee.am.management.handlers.oauth2.userdetails;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomUserDetails extends DefaultUser implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;
    private Collection<? extends GrantedAuthority> authorities;
    private boolean accountNonExpired;
    private boolean accountNonLocked;
    private boolean credentialsNonExpired;

    public CustomUserDetails(String username) {
        super(username);
    }

    public CustomUserDetails(User user, io.gravitee.am.identityprovider.api.User idpUser) {
        this(user.getUsername());
        setAdditonalInformation(user.getAdditionalInformation());
        setAccountExpired(!user.isAccountNonExpired());
        setEnabled(user.isEnabled());
        setRoles(idpUser.getRoles());
        accountNonExpired = user.isAccountNonExpired();
        accountNonLocked = user.isAccountNonLocked();
        credentialsNonExpired = user.isCredentialsNonExpired();
        if (idpUser.getRoles() != null) {
            authorities = idpUser.getRoles().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        }
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }
}

