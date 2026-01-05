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
package io.gravitee.am.identityprovider.gravitee.authentication;

import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class GraviteeAuthenticationProvider implements AuthenticationProvider {
    public static final String KEY_ORGANIZATION_ID = "org";

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private OrganizationUserService userService;

    @Override
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        final AuthenticationContext context = authentication.getContext();
        if (context == null || context.get(KEY_ORGANIZATION_ID) == null) {
            return Maybe.empty();
        }

        String username = (String) authentication.getPrincipal();
        return userService.findByUsernameAndSource(ReferenceType.ORGANIZATION, (String)context.get(KEY_ORGANIZATION_ID), username, "gravitee")
                .filter(user -> {
                    String presentedPassword = authentication.getCredentials().toString();

                    if (user.getPassword() == null) {
                        log.debug("Authentication failed: password is null");
                        return false;
                    }

                    if (!passwordEncoder.matches(presentedPassword, user.getPassword())) {
                        log.debug("Authentication failed: password does not match stored value");
                        return false;
                    }

                    return true;
                })
                .map(user -> {
                    DefaultUser idpUser = new DefaultUser(user.getUsername());
                    idpUser.setId(user.getId());
                    idpUser.setCredentials(user.getPassword());
                    idpUser.setEmail(user.getEmail());
                    idpUser.setAdditionalInformation(user.getAdditionalInformation() == null ? new HashMap<>() : user.getAdditionalInformation());
                    idpUser.setFirstName(user.getFirstName());
                    idpUser.setLastName(user.getLastName());
                    idpUser.setAccountExpired(!user.isAccountNonExpired());
                    idpUser.setCreatedAt(user.getCreatedAt());
                    idpUser.setEnabled(user.isEnabled());
                    idpUser.setUpdatedAt(user.getUpdatedAt());
                    return idpUser;
                });
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        // not relevant for Organization users
        return Maybe.empty();
    }
}
