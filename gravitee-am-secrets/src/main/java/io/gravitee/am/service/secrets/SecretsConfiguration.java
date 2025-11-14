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
package io.gravitee.am.service.secrets;

import io.gravitee.am.service.secrets.cache.SecretsCache;
import io.gravitee.am.service.secrets.cache.KryoSecureSecretsCache;
import io.gravitee.am.service.secrets.evaluators.SecretsResolvingPluginConfigurationEvaluator;
import io.gravitee.am.service.secrets.providers.SecretProviderRegistry;
import io.gravitee.am.service.secrets.providers.deployer.FromConfigurationSecretProviderDeployer;
import io.gravitee.am.service.secrets.resolver.CachingSecretResolver;
import io.gravitee.am.service.secrets.resolver.RegistryBasedSecretResolver;
import io.gravitee.am.service.secrets.resolver.SecretResolver;
import io.gravitee.node.secrets.plugins.SecretProviderPluginManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

import java.time.Duration;

/**
 * @author GraviteeSource Team
 */
@Configuration
public class SecretsConfiguration {

    @Bean
    public SecretProviderRegistry secretProviderRegistry() {
        return new SecretProviderRegistry();
    }

    @Bean(initMethod = "init")
    public FromConfigurationSecretProviderDeployer fromConfigurationSecretProviderDeployer(
            ConfigurableEnvironment configurableEnvironment,
            SecretProviderRegistry secretProviderRegistry,
            SecretProviderPluginManager secretProviderPluginManager

    ) {
        return new FromConfigurationSecretProviderDeployer(configurableEnvironment, secretProviderRegistry, secretProviderPluginManager);
    }

    @Bean
    public SecretsCache secretsCache(
            @Value("${domains.secrets.cache.ttl:3600000}") Integer cacheTtlMillis,
            @Value("${domains.secrets.cache.maxSize:#{null}}") Long cacheMaxSize
    ) {
        return new KryoSecureSecretsCache(Duration.ofMillis(cacheTtlMillis), cacheMaxSize);
    }

    @Bean
    public SecretResolver secretResolver(
            SecretProviderRegistry registry,
            SecretsCache secretsCache

    ) {
        return new CachingSecretResolver(
            new RegistryBasedSecretResolver(registry),
            secretsCache
        );
    }

    @Bean
    public SecretsResolvingPluginConfigurationEvaluator secretsPluginConfigurationEvaluator(SecretResolver resolver) {
        return new SecretsResolvingPluginConfigurationEvaluator(resolver);
    }
}
