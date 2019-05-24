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
package io.gravitee.am.gateway.handler.context.provider;

import io.gravitee.am.model.Domain;
import io.gravitee.el.TemplateContext;
import io.gravitee.el.TemplateVariableProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainTemplateVariableProvider implements TemplateVariableProvider, InitializingBean {

    @Autowired
    private Domain domain;

    private DomainProperties domainProperties;

    public void afterPropertiesSet() {
        domainProperties = new DomainProperties();
        domainProperties.setId(domain.getId());
        domainProperties.setName(domain.getName());
        domainProperties.setPath("/" + domain.getPath());
    }

    @Override
    public void provide(TemplateContext templateContext) {
        templateContext.setVariable("domain", domainProperties);
    }
}
