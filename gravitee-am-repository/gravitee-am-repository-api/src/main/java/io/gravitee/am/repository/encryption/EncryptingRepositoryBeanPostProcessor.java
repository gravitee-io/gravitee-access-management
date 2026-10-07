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
package io.gravitee.am.repository.encryption;

import io.gravitee.am.repository.encryption.FieldEncryptor.EncryptionKey;
import io.gravitee.node.logging.NodeLoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Wraps the repositories listed in {@link EncryptedFieldRegistry} in an {@link EncryptingRepositoryProxy}.
 * <p>
 * It must be declared in the repository plugin context: the plugin copies its repositories into the
 * parent context as ready-made singletons, so a post processor of the parent context never sees them.
 * <p>
 * The keys come from {@link EncryptionKeys}. Without any key, repositories are returned unchanged.
 *
 * @author GraviteeSource Team
 */
public class EncryptingRepositoryBeanPostProcessor implements BeanPostProcessor {

    private static final Logger LOGGER = NodeLoggerFactory.getLogger(EncryptingRepositoryBeanPostProcessor.class);

    private final FieldEncryptor encryptor;

    public EncryptingRepositoryBeanPostProcessor(Environment environment) {
        List<EncryptionKey> keys = EncryptionKeys.fromEnvironment(environment);
        this.encryptor = keys.isEmpty() ? null : FieldEncryptor.withKeys(keys);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (encryptor == null) {
            return bean;
        }
        return EncryptedFieldRegistry.forRepository(bean.getClass())
                .map(field -> {
                    LOGGER.debug("Encrypting field of {} on repository {}", field.type().getSimpleName(), beanName);
                    return EncryptingRepositoryProxy.wrap(bean, field, encryptor);
                })
                .orElse(bean);
    }
}
