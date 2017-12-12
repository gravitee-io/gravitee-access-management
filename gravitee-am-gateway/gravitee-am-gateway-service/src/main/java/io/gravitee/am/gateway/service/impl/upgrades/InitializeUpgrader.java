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
package io.gravitee.am.gateway.service.impl.upgrades;

import io.gravitee.am.gateway.service.ClientService;
import io.gravitee.am.gateway.service.DomainService;
import io.gravitee.am.gateway.service.IdentityProviderService;
import io.gravitee.am.gateway.service.exception.DomainNotFoundException;
import io.gravitee.am.gateway.service.model.*;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.GrantType;
import io.gravitee.am.model.IdentityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InitializeUpgrader implements Upgrader {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(InitializeUpgrader.class);

    private final static String ADMIN_DOMAIN = "admin";

    private final static String ADMIN_CLIENT_ID = "admin";
    private final static String ADMIN_CLIENT_SECRET = "admin-secret";
    private final static List<String> DEFAULT_SCOPES = Collections.singletonList("openid");

    @Autowired
    private DomainService domainService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Override
    public boolean upgrade() {
        logger.info("Looking for a registered {} domain", ADMIN_DOMAIN);

        try {
            Domain adminDomain = domainService.findById(ADMIN_DOMAIN);
            // update master flag
            // TODO: keep history in database to avoid this call
            if (!adminDomain.isMaster()) {
                logger.info("Set master flag for security domain {}", ADMIN_DOMAIN);
                domainService.setMasterDomain(adminDomain.getId(), true);
            }
            logger.info("{} domain already exists. Skipping.", ADMIN_DOMAIN);
        } catch (DomainNotFoundException dnfe) {
            //TODO: Use configuration to get admin values
            // Create a new admin domain
            logger.info("{} domain does not exists. Creating it.", ADMIN_DOMAIN);
            NewDomain adminDomain = new NewDomain();
            adminDomain.setName("admin");
            adminDomain.setDescription("AM Admin domain");
            Domain createdDomain = domainService.create(adminDomain);

            // Create a new admin client
            logger.info("Create an initial {} client", ADMIN_CLIENT_ID);
            NewClient adminClient = new NewClient();
            adminClient.setClientId(ADMIN_CLIENT_ID);
            adminClient.setClientSecret(ADMIN_CLIENT_SECRET);
            Client createdClient = clientService.create(createdDomain.getId(), adminClient);

            // Create an inline identity provider
            logger.info("Create an user-inline provider");
            NewIdentityProvider adminIdentityProvider = new NewIdentityProvider();
            adminIdentityProvider.setType("inline-am-idp");
            adminIdentityProvider.setName("Inline users");
            adminIdentityProvider.setConfiguration("{\"users\":[{\"firstname\":\"Administrator\",\"lastname\":\"\",\"username\":\"admin\",\"password\":\"adminadmin\"}]}");
            IdentityProvider createdIdentityProvider = identityProviderService.create(createdDomain.getId(), adminIdentityProvider);

            // Associate the identity provider to the client and enabled it
            logger.info("Associate user-inline provider to previously created client");
            UpdateClient updateClient = new UpdateClient();
            updateClient.setAccessTokenValiditySeconds(createdClient.getAccessTokenValiditySeconds());
            updateClient.setRefreshTokenValiditySeconds(createdClient.getRefreshTokenValiditySeconds());
            updateClient.setAuthorizedGrantTypes(Collections.singletonList(GrantType.IMPLICIT.type()));
            updateClient.setScopes(DEFAULT_SCOPES);
            updateClient.setAutoApproveScopes(updateClient.getScopes());
            updateClient.setIdentities(Collections.singleton(createdIdentityProvider.getId()));
            updateClient.setEnabled(true);
            clientService.update(createdDomain.getId(), createdClient.getId(), updateClient);

            // Enabled the domain
            logger.info("Start {} security domain", ADMIN_DOMAIN);
            UpdateDomain updateDomain = new UpdateDomain();
            updateDomain.setName(createdDomain.getName());
            updateDomain.setDescription(createdDomain.getDescription());
            updateDomain.setEnabled(true);
            updateDomain.setPath(createdDomain.getPath());
            domainService.update(createdDomain.getId(), updateDomain);

            // Set master flag
            logger.info("Set master flag for security domain {}", ADMIN_DOMAIN);
            domainService.setMasterDomain(createdDomain.getId(), true);
        }

        return true;
    }
}
