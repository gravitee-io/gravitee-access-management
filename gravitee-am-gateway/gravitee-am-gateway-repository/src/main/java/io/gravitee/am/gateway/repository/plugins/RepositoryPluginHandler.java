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
package io.gravitee.am.gateway.repository.plugins;

import io.gravitee.am.repository.Repository;
import io.gravitee.am.repository.Scope;
import io.gravitee.plugin.core.api.*;
import io.gravitee.plugin.core.internal.AnnotationBasedPluginContextConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author David BRASSELY (brasseld at gmail.com)
 * @author GraviteeSource Team
 */
public class RepositoryPluginHandler implements PluginHandler, InitializingBean {

    private final static Logger LOGGER = LoggerFactory.getLogger(RepositoryPluginHandler.class);

    @Autowired
    private Environment environment;

    @Autowired
    private PluginContextFactory pluginContextFactory;

    @Autowired
    private PluginClassLoaderFactory pluginClassLoaderFactory;

    @Autowired
    private ApplicationContext applicationContext;

    private final Map<Scope, Repository> repositories = new HashMap<>();
    private final Map<Scope, String> repositoryTypeByScope = new HashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        // The gateway need 2 repositories :
        // 1_ Management
        lookForRepositoryType(Scope.MANAGEMENT);
        // 2_ OAuth2
        lookForRepositoryType(Scope.OAUTH2);
    }

    @Override
    public boolean canHandle(Plugin plugin) {
        return plugin.type() == PluginType.REPOSITORY;
    }

    @Override
    public void handle(Plugin plugin) {
        try {
            ClassLoader classloader = pluginClassLoaderFactory.getOrCreateClassLoader(plugin, this.getClass().getClassLoader());

            final Class<?> repositoryClass = classloader.loadClass(plugin.clazz());
            LOGGER.info("Register a new repository plugin: {} [{}]", plugin.id(), plugin.clazz());

            Assert.isAssignable(Repository.class, repositoryClass);

            Repository repository = createInstance((Class<Repository>) repositoryClass);
            for (Scope scope : repository.scopes()) {
                if (!repositories.containsKey(scope)) {
                    String requiredRepositoryType = repositoryTypeByScope.get(scope);

                    // Load only repository plugin for a given scope (provided in the configuration)
                    if (repository.type().equalsIgnoreCase(requiredRepositoryType)) {

                        boolean loaded = false;
                        int tries = 0;

                        while (! loaded) {
                            if (tries > 0) {
                                // Wait for 5 seconds before giving an other try
                                Thread.sleep(5000);
                            }
                            loaded = loadRepository(scope, repository, plugin);
                            tries++;


                            if (! loaded) {
                                LOGGER.error("Unable to load repository {} for scope {}. Retry in 5 seconds...", scope, plugin.id());
                            }
                        }
                    } else {
                        LOGGER.debug("Scoped repository [{}] must be loaded by {}. Skipping registration",
                                scope, requiredRepositoryType);
                    }
                } else {
                    LOGGER.warn("Repository scope {} already loaded by {}", scope,
                            repositories.get(scope));
                }
            }
        } catch (Exception iae) {
            LOGGER.error("Unexpected error while create repository instance", iae);
        }
    }

    private boolean loadRepository(Scope scope, Repository repository, Plugin plugin) {

        LOGGER.info("Repository [{}] loaded by {}", scope, repository.type());

        // Not yet loaded, let's mount the repository in application context
        try {
            ApplicationContext repoApplicationContext = pluginContextFactory.create(
                    new AnnotationBasedPluginContextConfigurer(plugin) {
                        @Override
                        public Set<Class<?>> configurations() {
                            return Collections.singleton(repository.configuration(scope));
                        }
                    });

            registerRepositoryDefinitions(repository, repoApplicationContext);
            repositories.put(scope, repository);
            return true;
        } catch (Exception iae) {
            LOGGER.error("Unexpected error while creating context for repository instance", iae);
            pluginContextFactory.remove(plugin);
            return false;
        }
    }


    private void registerRepositoryDefinitions(Repository repository, ApplicationContext repoApplicationContext) {
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory)
                ((ConfigurableApplicationContext) applicationContext).getBeanFactory();

        String [] beanNames = repoApplicationContext.getBeanDefinitionNames();
        for(String beanName : beanNames) {
            Object repositoryClassInstance = repoApplicationContext.getBean(beanName);
            if ((beanName.endsWith("Repository") || beanName.endsWith("Manager")) && ! repository.getClass().equals(repositoryClassInstance.getClass())) {
                Class<?> repositoryObjectClass = repositoryClassInstance.getClass();
                if (repositoryObjectClass.getInterfaces().length > 0) {
                    Class<?> repositoryItfClass = repositoryObjectClass.getInterfaces()[0];
                    LOGGER.debug("Register {} [{}] in gateway context", beanName, repositoryItfClass);
                    beanFactory.registerSingleton(repositoryItfClass.getName(),
                            repositoryClassInstance);
                }
            }
        }
    }

    private String lookForRepositoryType(Scope scope) throws Exception {
        String repositoryType = environment.getProperty(scope.getName() + ".type");
        LOGGER.info("Loading repository for scope {}: {}", scope, repositoryType);

        if (repositoryType == null || repositoryType.isEmpty()) {
            LOGGER.error("No repository type defined in configuration for {}", scope.getName());
            throw new IllegalStateException("No repository type defined in configuration for " + scope.getName());
        }

        repositoryTypeByScope.put(scope, repositoryType);
        return repositoryType;
    }

    private <T> T createInstance(Class<T> clazz) throws Exception {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            LOGGER.error("Unable to instantiate class: {}", ex);
            throw ex;
        }
    }
}
