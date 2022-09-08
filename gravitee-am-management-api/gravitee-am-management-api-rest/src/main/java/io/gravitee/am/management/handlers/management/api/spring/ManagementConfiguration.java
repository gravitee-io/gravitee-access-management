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
package io.gravitee.am.management.handlers.management.api.spring;

import io.gravitee.am.management.handlers.management.api.authentication.manager.form.FormManager;
import io.gravitee.am.management.handlers.management.api.authentication.manager.form.impl.FormManagerImpl;
import io.gravitee.am.management.handlers.management.api.authentication.service.AuthenticationService;
import io.gravitee.am.management.handlers.management.api.authentication.service.impl.AuthenticationServiceImpl;
import io.gravitee.am.management.handlers.management.api.preview.PreviewService;
import io.gravitee.am.management.handlers.management.api.spring.security.SecurityConfiguration;
import io.gravitee.am.management.handlers.management.api.spring.security.WebMvcConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@ComponentScan({"io.gravitee.am.management.handlers.management.api.resources.enhancer"})
@Import({WebMvcConfiguration.class, SecurityConfiguration.class})
public class ManagementConfiguration {

    @Bean
    public AuthenticationService authenticationService() {
        return new AuthenticationServiceImpl();
    }

    @Bean
    public FormManager formManager() {
        return new FormManagerImpl();
    }

    @Bean
    public PreviewService previewService() {
        return new PreviewService();
    }
}
