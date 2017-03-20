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
package io.gravitee.am.handler.spring;

import io.gravitee.am.gateway.core.context.servlet.ServletContext;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.util.Collections;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class SpringServletContext<T> implements ServletContext<T> {

    private ConfigurableApplicationContext applicationContext;
    private ApplicationContext rootApplicationContext;

    protected WebApplicationContext applicationContext() {
        if (applicationContext == null) {
            AnnotationConfigWebApplicationContext webApplicationContext = new AnnotationConfigWebApplicationContext();

            Set<Class<?>> annotatedClasses = annotatedClasses();
            if (annotatedClasses != null) {
                annotatedClasses.iterator().forEachRemaining(webApplicationContext::register);
            }

            Set<? extends BeanFactoryPostProcessor> beanFactoryPostProcessors = beanFactoryPostProcessors();
            if (beanFactoryPostProcessors != null) {
                beanFactoryPostProcessors.iterator().forEachRemaining(webApplicationContext::addBeanFactoryPostProcessor);
            }

            if (this.rootApplicationContext != null) {
                webApplicationContext.setParent(this.rootApplicationContext);
                webApplicationContext.setEnvironment((ConfigurableEnvironment) rootApplicationContext.getEnvironment());
            }

            applicationContext = webApplicationContext;
        }

        return (WebApplicationContext) applicationContext;
    }

    protected Set<Class<?>> annotatedClasses() {
        return Collections.emptySet();
    }

    protected Set<? extends BeanFactoryPostProcessor> beanFactoryPostProcessors() {
        return Collections.emptySet();
    }

    public void setRootApplicationContext(ApplicationContext rootApplicationContext) {
        this.rootApplicationContext = rootApplicationContext;
    }
}
