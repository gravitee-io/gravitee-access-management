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
package io.gravitee.am.management.service.impl.upgrades;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author Islem TRIKI (islem.triki at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DefaultIdentityProviderUpgrader extends AsyncUpgrader {

    private static final Logger logger = LoggerFactory.getLogger(DefaultIdentityProviderUpgrader.class);

    private final IdentityProviderService identityProviderService;
    private final IdentityProviderManager identityProviderManager;
    private final ObjectMapper mapper;

    public DefaultIdentityProviderUpgrader(IdentityProviderService identityProviderService,
                                           IdentityProviderManager identityProviderManager) {
        this.identityProviderService = identityProviderService;
        this.identityProviderManager = identityProviderManager;
        this.mapper = new ObjectMapper();
    }


    @Override
    public Completable doUpgrade() {
         return Completable.fromPublisher(identityProviderService.findAll()
                .filter(IdentityProvider::isSystem)
                .flatMapSingle(this::updateDefaultIdp)
                 .doOnNext(idp -> logger.info("updated IDP: id={}", idp.getId())));

    }

    private Single<IdentityProvider> updateDefaultIdp(IdentityProvider identityProvider) {

        UpdateIdentityProvider updateIdentityProvider = new UpdateIdentityProvider();
        updateIdentityProvider.setDomainWhitelist(identityProvider.getDomainWhitelist());
        updateIdentityProvider.setMappers(identityProvider.getMappers());
        updateIdentityProvider.setName(identityProvider.getName());
        updateIdentityProvider.setRoleMapper(identityProvider.getRoleMapper());
        Map<String, Object> configMap = identityProviderManager.createProviderConfiguration(identityProvider.getReferenceId(), null);
        try {
            Map<String, Object> existingConfigMap = mapper.readValue(identityProvider.getConfiguration(), Map.class);
            configMap.put("passwordEncoder", existingConfigMap.get("passwordEncoder"));
            configMap.put("passwordEncoderOptions", existingConfigMap.get("passwordEncoderOptions"));
            updateIdentityProvider.setConfiguration(mapper.writeValueAsString(configMap));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize the default idp configuration for domain '" + identityProvider.getReferenceId() + "'", e);
        }

        return identityProviderService.update(identityProvider.getReferenceId(), identityProvider.getId(), updateIdentityProvider, true);
    }

    @Override
    public int getOrder() {
        return UpgraderOrder.DEFAULT_IDP_UPGRADER;
    }
}
