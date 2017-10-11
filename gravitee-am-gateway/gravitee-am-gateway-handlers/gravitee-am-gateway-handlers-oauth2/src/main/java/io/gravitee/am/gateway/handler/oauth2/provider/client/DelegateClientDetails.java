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
package io.gravitee.am.gateway.handler.oauth2.provider.client;

import io.gravitee.am.model.Client;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;

import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DelegateClientDetails extends BaseClientDetails {

    private final Client client;

    DelegateClientDetails(Client client) {
        super();
        this.client = client;
        setAccessTokenValiditySeconds(client.getAccessTokenValiditySeconds());
        setRefreshTokenValiditySeconds(client
                .getRefreshTokenValiditySeconds());
        setAuthorizedGrantTypes(client.getAuthorizedGrantTypes().stream().map(grantType -> grantType.toLowerCase()).collect(Collectors.toSet()));
        setClientId(client.getClientId());
        setClientSecret(client.getClientSecret());
        setRegisteredRedirectUri(client.getRedirectUris() != null ? new HashSet<>(client.getRedirectUris()) : null);
        setScope(client.getScopes());
        setAutoApproveScopes(client.getAutoApproveScopes() != null ? new HashSet<>(client.getAutoApproveScopes()) : Collections.emptySet());
//        setResourceIds(client.getResourceIds());
    }

    public Client getClient() {
        return client;
    }
}
