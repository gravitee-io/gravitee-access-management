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
package io.gravitee.am.management.service.impl.plugins;

import io.gravitee.am.management.certificate.core.CertificatePluginManager;
import io.gravitee.am.management.service.CertificatePluginService;
import io.gravitee.am.service.exception.CertificateNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.CertificatePlugin;
import io.gravitee.plugin.core.api.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class CertificatePluginServiceImpl implements CertificatePluginService {

    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(CertificatePluginServiceImpl.class);

    @Autowired
    private CertificatePluginManager certificatePluginManager;

    @Override
    public Set<CertificatePlugin> findAll() {
        try {
            LOGGER.debug("List all certificates");
            return certificatePluginManager.getAll()
                    .stream()
                    .map(this::convert)
                    .collect(Collectors.toSet());
        } catch (Exception ex) {
            LOGGER.error("An error occurs while trying to list all certificates", ex);
            throw new TechnicalManagementException("An error occurs while trying to list all certificates", ex);
        }
    }

    @Override
    public CertificatePlugin findById(String certificatePluginId) {
        LOGGER.debug("Find certificate by ID: {}", certificatePluginId);
        Plugin certificate = certificatePluginManager.findById(certificatePluginId);

        if (certificate == null) {
            throw new CertificateNotFoundException(certificatePluginId);
        }

        return convert(certificate);
    }

    @Override
    public String getSchema(String certificatePluginId) {
        try {
            LOGGER.debug("Find certificate schema by ID: {}", certificatePluginId);
            return certificatePluginManager.getSchema(certificatePluginId);
        } catch (IOException ioex) {
            LOGGER.error("An error occurs while trying to get schema for certificate {}", certificatePluginId, ioex);
            throw new TechnicalManagementException("An error occurs while trying to get schema for certificate " + certificatePluginId, ioex);
        }
    }

    private CertificatePlugin convert(Plugin certificatePlugin) {
        CertificatePlugin plugin = new CertificatePlugin();
        plugin.setId(certificatePlugin.manifest().id());
        plugin.setName(certificatePlugin.manifest().name());
        plugin.setDescription(certificatePlugin.manifest().description());
        plugin.setVersion(certificatePlugin.manifest().version());
        return plugin;
    }
}
