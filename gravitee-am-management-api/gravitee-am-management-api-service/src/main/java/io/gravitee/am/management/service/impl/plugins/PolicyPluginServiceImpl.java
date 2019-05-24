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

import io.gravitee.am.management.service.PolicyPluginService;
import io.gravitee.am.plugins.policy.core.PolicyPluginManager;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.PolicyPlugin;
import io.gravitee.plugin.core.api.Plugin;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class PolicyPluginServiceImpl implements PolicyPluginService {

    private final Logger LOGGER = LoggerFactory.getLogger(PolicyPluginServiceImpl.class);

    @Autowired
    private PolicyPluginManager policyPluginManager;

    @Override
    public Single<List<PolicyPlugin>> findAll() {
        LOGGER.debug("List all policy plugins");
        return Observable.fromIterable(policyPluginManager.getAll())
                .map(this::convert)
                .toList();
    }

    @Override
    public Maybe<PolicyPlugin> findById(String policyId) {
        LOGGER.debug("Find policy plugin by ID: {}", policyId);
        return Maybe.create(emitter -> {
            try {
                PolicyPlugin policy = convert(policyPluginManager.get(policyId));
                if (policy != null) {
                    emitter.onSuccess(policy);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception ex) {
                LOGGER.error("An error occurs while trying to get policy plugin : {}", policyId, ex);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get policy plugin : " + policyId, ex));
            }
        });
    }

    @Override
    public Maybe<String> getSchema(String policyId) {
        LOGGER.debug("Find policy plugin schema by ID: {}", policyId);
        return Maybe.create(emitter -> {
            try {
                String schema = policyPluginManager.getSchema(policyId);
                if (schema != null) {
                    emitter.onSuccess(schema);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                LOGGER.error("An error occurs while trying to get schema for policy plugin {}", policyId, e);
                emitter.onError(new TechnicalManagementException("An error occurs while trying to get schema for policy plugin " + policyId, e));
            }
        });
    }

    private PolicyPlugin convert(Plugin policyPlugin) {
        PolicyPlugin plugin = new PolicyPlugin();
        plugin.setId(policyPlugin.manifest().id());
        plugin.setName(policyPlugin.manifest().name());
        plugin.setDescription(policyPlugin.manifest().description());
        plugin.setVersion(policyPlugin.manifest().version());
        return plugin;
    }
}
