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
package io.gravitee.am.plugins.policy.core.impl;

import com.google.common.base.Predicate;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.gateway.policy.PolicyMetadata;
import io.gravitee.am.gateway.policy.impl.PolicyImpl;
import io.gravitee.am.gateway.policy.impl.PolicyMetadataBuilder;
import io.gravitee.am.plugins.policy.core.PolicyConfigurationFactory;
import io.gravitee.am.plugins.policy.core.PolicyPluginManager;
import io.gravitee.plugin.core.api.ConfigurablePluginManager;
import io.gravitee.plugin.core.api.PluginClassLoader;
import io.gravitee.plugin.core.api.PluginClassLoaderFactory;
import io.gravitee.plugin.policy.PolicyPlugin;
import io.gravitee.plugin.policy.internal.PolicyMethodResolver;
import io.gravitee.policy.api.PolicyConfiguration;
import org.reflections.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.reflections.ReflectionUtils.withModifier;
import static org.reflections.ReflectionUtils.withParametersCount;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PolicyPluginManagerImpl implements PolicyPluginManager {

    private final Logger logger = LoggerFactory.getLogger(PolicyPluginManagerImpl.class);

    /**
     * Cache of constructor by policy
     */
    private Map<Class<?>, Constructor<?>> constructors = new HashMap<>();

    @Autowired
    private ConfigurablePluginManager<PolicyPlugin> pluginManager;

    @Autowired
    private PolicyConfigurationFactory policyConfigurationFactory;

    @Autowired
    private PluginClassLoaderFactory pluginClassLoaderFactory;

    @Override
    public Collection<PolicyPlugin> getAll() {
        return pluginManager.findAll();
    }

    @Override
    public PolicyPlugin get(String policyId) {
        return pluginManager.get(policyId);
    }

    @Override
    public String getSchema(String policyId) throws IOException {
        return pluginManager.getSchema(policyId);
    }

    @Override
    public String getIcon(String policyId) throws IOException {
        return pluginManager.getIcon(policyId);
    }

    @Override
    public String getDocumentation(String policyId) throws IOException {
        return pluginManager.getDocumentation(policyId);
    }

    @Override
    public Policy create(String type, String configuration) {
        logger.debug("Creating a policy for [{}]", type);
        PolicyPlugin policyPlugin = pluginManager.get(type);

        if (policyPlugin != null) {
            try {
                // create policy configuration
                PluginClassLoader pluginClassLoader = pluginClassLoaderFactory.getOrCreateClassLoader(policyPlugin);
                Class<? extends PolicyConfiguration> configurationClass =  (Class<? extends PolicyConfiguration>) ClassUtils.forName(policyPlugin.configuration().getName(), pluginClassLoader);
                PolicyConfiguration policyConfiguration = policyConfigurationFactory.create(configurationClass, configuration);

                // create policy instance
                Object policyInst;

                // Prepare metadata
                Class<?> policyClass = ClassUtils.forName(policyPlugin.policy().getName(), pluginClassLoader);
                PolicyMetadataBuilder builder = new PolicyMetadataBuilder();
                builder
                        .setId(policyPlugin.id())
                        .setPolicy(policyClass)
                        .setConfiguration(configurationClass)
                        .setClassLoader(pluginClassLoader)
                        .setMethods(new PolicyMethodResolver().resolve(policyClass));
                PolicyMetadata policyMetadata = builder.build();

                // Create instance with matching constructor
                Constructor<?> constr = lookingForConstructor(policyMetadata.policy());
                if (constr != null) {
                    try {
                        if (constr.getParameterCount() > 0) {
                            policyInst = constr.newInstance(policyConfiguration);
                        } else {
                            policyInst = constr.newInstance();
                        }
                        if (policyInst != null) {
                            return PolicyImpl
                                    .target(policyInst)
                                    .definition(policyMetadata)
                                    .build();
                        }
                    } catch (IllegalAccessException | InstantiationException | InvocationTargetException ex) {
                        logger.error("Unable to instantiate policy {}", policyMetadata.policy().getName(), ex);
                    }
                }
            } catch (Exception ex) {
                logger.error("An unexpected error occurs while loading policy", ex);
            }
        } else {
            logger.error("No policy is registered for type {}", type);
            throw new IllegalStateException("No policy is registered for type " + type);
        }
        return null;
    }

    private Constructor<?> lookingForConstructor(Class<?> policyClass) {
        Constructor constructor = constructors.get(policyClass);
        if (constructor == null) {
            logger.debug("Looking for a constructor to inject policy configuration");

            Set<Constructor> policyConstructors =
                    ReflectionUtils.getConstructors(policyClass,
                            withModifier(Modifier.PUBLIC),
                            withParametersAssignableFrom(PolicyConfiguration.class),
                            withParametersCount(1));

            if (policyConstructors.isEmpty()) {
                logger.debug("No configuration can be injected for {} because there is no valid constructor. " +
                        "Using default empty constructor.", policyClass.getName());
                try {
                    constructor = policyClass.getConstructor();
                } catch (NoSuchMethodException nsme) {
                    logger.error("Unable to find default empty constructor for {}", policyClass.getName(), nsme);
                }
            } else if (policyConstructors.size() == 1) {
                constructor = policyConstructors.iterator().next();
            } else {
                logger.info("Too much constructors to instantiate policy {}", policyClass.getName());
            }

            constructors.put(policyClass, constructor);
        }

        return constructor;
    }

    private static Predicate<Member> withParametersAssignableFrom(final Class... types) {
        return input -> {
            if (input != null) {
                Class<?>[] parameterTypes = parameterTypes(input);
                if (parameterTypes.length == types.length) {
                    for (int i = 0; i < parameterTypes.length; i++) {
                        if (!types[i].isAssignableFrom(parameterTypes[i]) ||
                                (parameterTypes[i] == Object.class && types[i] != Object.class)) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            return false;
        };
    }

    private static Class[] parameterTypes(Member member) {
        return member != null ?
                member.getClass() == Method.class ? ((Method) member).getParameterTypes() :
                        member.getClass() == Constructor.class ? ((Constructor) member).getParameterTypes() : null : null;
    }
}
