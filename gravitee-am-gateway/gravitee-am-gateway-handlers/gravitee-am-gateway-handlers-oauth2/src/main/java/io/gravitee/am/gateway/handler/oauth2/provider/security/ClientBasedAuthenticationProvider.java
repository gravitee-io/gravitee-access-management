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
package io.gravitee.am.gateway.handler.oauth2.provider.security;


import io.gravitee.am.gateway.handler.oauth2.provider.client.DelegateClientDetails;
import io.gravitee.am.gateway.handler.oauth2.security.IdentityProviderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.ClientDetailsService;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientBasedAuthenticationProvider implements AuthenticationProvider {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(ClientBasedAuthenticationProvider.class);

    @Autowired
    private ClientDetailsService clientDetailsService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Map<String, String> details = (Map<String, String>) authentication.getDetails();
        String clientId = details.get("client_id");

        // TODO: is there an other way to access client details without calling storage again ?
        try {
            ClientDetails clientDetails = clientDetailsService.loadClientByClientId(clientId);
            if (clientDetails instanceof DelegateClientDetails) {
                Set<String> identities = ((DelegateClientDetails) clientDetails).getClient().getIdentities();
                Iterator<String> iter = identities.iterator();
                io.gravitee.am.identityprovider.api.User user = null;
                while (iter.hasNext() && user == null) {
                    String provider = iter.next();
                    io.gravitee.am.identityprovider.api.AuthenticationProvider authenticationProvider =
                            identityProviderManager.get(provider);

                    try {
                        user = authenticationProvider.loadUserByUsername(new io.gravitee.am.identityprovider.api.Authentication() {
                            @Override
                            public Object getCredentials() {
                                return authentication.getCredentials().toString();
                            }

                            @Override
                            public Object getPrincipal() {
                                return authentication.getName();
                            }
                        });
                    } catch (Exception ex) {
                        logger.info("Unable to authenticate user {} with provider {}",
                                authentication.getName(), provider);
                    }
                }

                if (user != null) {
                    return new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword(),
                            AuthorityUtils.NO_AUTHORITIES);
                }

                throw new BadCredentialsException("Bad credentials.");
            }
        } catch (Exception ex) {}

        throw new BadCredentialsException("Authentication can not be handle");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(
                UsernamePasswordAuthenticationToken.class);
    }
}
