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
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.CertificatePlugin;
import io.gravitee.plugin.core.api.Plugin;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
    public Single<Set<CertificatePlugin>> findAll() {
        LOGGER.debug("List all certificate plugins");
        return Single.create(emitter -> {
            try {
                emitter.onSuccess(certificatePluginManager.getAll()
                        .stream()
                        .map(this::convert)
                        .collect(Collectors.toSet()));
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to list all certificate plugins", ex);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to list all certificate plugins", ex));
            }
        });
    }

    @Override
    public Maybe<CertificatePlugin> findById(String certificatePluginId) {
        LOGGER.debug("Find certificate plugin by ID: {}", certificatePluginId);
        return Maybe.create(emitter -> {
            try {
                Plugin certificate = certificatePluginManager.findById(certificatePluginId);
                if (certificate != null) {
                    emitter.onSuccess(convert(certificate));
                } else {
                    emitter.onComplete();
                }
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to get certificate plugin : {}", certificatePluginId, ex);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get certificate plugin : " + certificatePluginId, ex));
            }
        });
    }

    @Override
    public Maybe<String> getSchema(String certificatePluginId) {
        LOGGER.debug("Find certificate plugin schema by ID: {}", certificatePluginId);
        return Maybe.create(emitter -> {
            try {
                String schema = certificatePluginManager.getSchema(certificatePluginId);
                if (schema != null) {
                    emitter.onSuccess(schema);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get schema for certificate plugin {}", certificatePluginId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get schema for certificate plugin " + certificatePluginId, e));
            }
        });
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
