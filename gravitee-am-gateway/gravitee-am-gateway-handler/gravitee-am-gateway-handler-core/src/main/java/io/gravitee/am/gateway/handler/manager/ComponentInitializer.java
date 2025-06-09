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
package io.gravitee.am.gateway.handler.manager;

import io.gravitee.common.component.LifecycleComponent;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;

@Builder
@Slf4j
public class ComponentInitializer {
    @Default private List<Class<? extends LifecycleComponent>> components = new ArrayList<>();
    private final ApplicationContext applicationContext;

    public void initialize() {
        List<LifecycleComponent> initialized = new ArrayList<>();

        try {
            components.forEach(componentClass -> {
                LifecycleComponent lifecyclecomponent = applicationContext.getBean(componentClass);
                try {
                    lifecyclecomponent.start();
                    initialized.add(lifecyclecomponent);
                } catch (Exception e) {
                    log.error("An error occurs while starting component {}", componentClass.getSimpleName(), e);
                }
            });
        } catch (BeansException e){
            log.error("Reverting bootstrap chain of components {}", components);
            revert(initialized);
            throw e;
        }

    }


    private void revert(List<LifecycleComponent> components) {
        components.forEach(component -> {
            try {
                component.stop();
            } catch (Exception e) {
                log.error("Couldn't stop component {}", component, e);
            }
        });
    }
}
