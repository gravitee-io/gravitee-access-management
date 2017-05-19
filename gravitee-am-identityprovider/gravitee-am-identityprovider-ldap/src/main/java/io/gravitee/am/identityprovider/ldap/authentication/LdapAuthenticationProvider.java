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
package io.gravitee.am.identityprovider.ldap.authentication;

import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.ldap.LdapIdentityProviderMapper;
import io.gravitee.am.identityprovider.ldap.authentication.spring.LdapAuthenticationProviderConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ldap.NamingException;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.ldap.authentication.LdapAuthenticator;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(LdapAuthenticationProviderConfiguration.class)
public class LdapAuthenticationProvider implements AuthenticationProvider {

    @Autowired
    private LdapAuthenticator authenticator;

    @Autowired
    private LdapIdentityProviderMapper mapper;

    @Override
    public User loadUserByUsername(Authentication authentication) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            DirContextOperations authenticate;
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                authenticate = authenticator.authenticate(new UsernamePasswordAuthenticationToken(
                        authentication.getPrincipal(), authentication.getCredentials()));
            } finally {
                Thread.currentThread().setContextClassLoader(classLoader);
            }

            DefaultUser user = new DefaultUser(authenticate.getNameInNamespace());
            Map<String, Object> claims = new HashMap<>();
            if (mapper.getMappers() != null) {
                mapper.getMappers().forEach((k, v) -> {
                    claims.put(k, authenticate.getStringAttribute(v));
                });
            } else {
                // default values
                claims.put("sub", authenticate.getStringAttribute("uid"));
                claims.put("email", authenticate.getStringAttribute("mail"));
                claims.put("name", authenticate.getStringAttribute("displayname"));
                claims.put("given_name", authenticate.getStringAttribute("givenname"));
                claims.put("family_name", authenticate.getStringAttribute("sn"));
            }

            user.setAdditonalInformation(claims);

            return user;

        } catch (UsernameNotFoundException notFound) {
            throw notFound;
        } catch (NamingException ldapAccessFailure) {
            throw new InternalAuthenticationServiceException(
                    ldapAccessFailure.getMessage(), ldapAccessFailure);
        }
    }
}
