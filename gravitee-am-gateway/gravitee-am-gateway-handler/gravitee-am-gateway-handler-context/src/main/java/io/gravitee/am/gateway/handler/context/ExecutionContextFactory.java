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
package io.gravitee.am.gateway.handler.context;

import io.gravitee.el.TemplateVariableProvider;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.context.MutableExecutionContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExecutionContextFactory implements InitializingBean {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TemplateVariableProviderFactory templateVariableProviderFactory;

    private List<TemplateVariableProvider> providers;

    @Override
    public void afterPropertiesSet() throws Exception {
        providers = templateVariableProviderFactory.getTemplateVariableProviders();
    }

    public ExecutionContext create(ExecutionContext wrapped) {
        ReactableExecutionContext context = new ReactableExecutionContext(
                (MutableExecutionContext) wrapped, applicationContext);
        context.setProviders(providers);
        return context;
    }
}
