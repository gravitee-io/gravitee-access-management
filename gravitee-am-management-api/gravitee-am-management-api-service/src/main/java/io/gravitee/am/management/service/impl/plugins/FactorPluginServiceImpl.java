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

import io.gravitee.am.management.service.FactorPluginService;
import io.gravitee.am.plugins.factor.core.FactorPluginManager;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.FactorPlugin;
import io.gravitee.plugin.core.api.Plugin;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.springframework.stereotype.Component;

import java.util.List;
import lombok.CustomLog;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@CustomLog
public class FactorPluginServiceImpl extends AbstractPluginService implements FactorPluginService {


    private FactorPluginManager factorPluginManager;

    public FactorPluginServiceImpl(FactorPluginManager factorPluginManager) {
        super(factorPluginManager);
        this.factorPluginManager = factorPluginManager;
    }

    @Override
    public Single<List<FactorPlugin>> findAll() {
        log.debug("List all factor plugins");
        return Observable.fromIterable(factorPluginManager.findAll(true))
                .map(this::convert)
                .toList();
    }

    @Override
    public Maybe<FactorPlugin> findById(String factorId) {
        log.debug("Find factor plugin by ID: {}", factorId);
        return Maybe.create(emitter -> {
            try {
                Plugin authenticator = factorPluginManager.findById(factorId);
                if (authenticator != null) {
                    emitter.onSuccess(convert(authenticator));
                } else {
                    emitter.onComplete();
                }
            } catch (Exception ex) {
                log.error("An error occurs while trying to get factor plugin : {}", factorId, ex);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get factor plugin : " + factorId, ex));
            }
        });
    }

    @Override
    public Maybe<String> getSchema(String factorId) {
        log.debug("Find factor plugin schema by ID: {}", factorId);
        return Maybe.create(emitter -> {
            try {
                String schema = factorPluginManager.getSchema(factorId);
                if (schema != null) {
                    emitter.onSuccess(schema);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                log.error("An error occurs while trying to get schema for factor plugin {}", factorId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get schema for factor plugin " + factorId, e));
            }
        });
    }

    private FactorPlugin convert(Plugin plugin) {
        var factorPlugin = new FactorPlugin();
        factorPlugin.setId(plugin.manifest().id());
        factorPlugin.setName(plugin.manifest().name());
        factorPlugin.setDescription(plugin.manifest().description());
        factorPlugin.setVersion(plugin.manifest().version());
        factorPlugin.setCategory(plugin.manifest().category());
        factorPlugin.setDeployed(plugin.deployed());
        factorPlugin.setFeature(plugin.manifest().feature());
        return factorPlugin;
    }
}
