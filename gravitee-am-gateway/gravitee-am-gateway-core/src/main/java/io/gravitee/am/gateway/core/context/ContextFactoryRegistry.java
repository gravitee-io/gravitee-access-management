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
package io.gravitee.am.gateway.core.context;

import io.gravitee.am.gateway.core.spring.AbstractSpringFactoriesLoader;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ContextFactoryRegistry extends AbstractSpringFactoriesLoader<ContextFactory> {

    public Context create(Object obj) {
        for (ContextFactory factory : getFactoriesInstances()) {
            if (factory.canHandle(obj)) {
                return factory.create(obj);
            }
        }

        throw new IllegalStateException(
                String.format("Unable to create a security context for %s", obj));
    }

    @Override
    protected Class<ContextFactory> getObjectType() {
        return ContextFactory.class;
    }
}
