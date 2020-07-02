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
package io.gravitee.am.gateway.handler.discovery.service.impl;

import io.gravitee.am.gateway.handler.discovery.service.DiscoveryService;
import io.gravitee.am.gateway.handler.discovery.service.WellKnownMetadata;
import io.gravitee.am.model.Domain;
import org.springframework.beans.factory.annotation.Autowired;

import static io.gravitee.am.gateway.handler.discovery.constants.DiscoveryConstants.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class DiscoveryServiceImpl implements DiscoveryService {

    @Autowired
    private Domain domain;

    @Override
    public WellKnownMetadata getConfiguration(String basePath) {
        WellKnownMetadata wellKnown = new WellKnownMetadata()
                .setOpenidConfiguration(getEndpointAbsoluteURL(basePath, OPENID_DISCOVERY_PATH));

        if(domain.getUma()!=null && domain.getUma().isEnabled()) {
            wellKnown.setUmaConfiguration(getEndpointAbsoluteURL(basePath, UMA_DISCOVERY_PATH));
        }

        if(domain.getScim()!=null && domain.getScim().isEnabled()) {
            wellKnown.setScim2Configuration(getEndpointAbsoluteURL(basePath, SCIM_DISCOVERY_PATH));
        }

        return wellKnown;
    }

    private String getEndpointAbsoluteURL(String basePath, String endpointPath) {
        return (basePath + endpointPath);
    }
}
