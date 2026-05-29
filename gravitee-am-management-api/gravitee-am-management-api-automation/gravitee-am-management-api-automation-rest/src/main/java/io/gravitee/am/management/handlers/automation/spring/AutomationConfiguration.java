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
package io.gravitee.am.management.handlers.automation.spring;

import io.gravitee.am.management.handlers.automation.spring.security.AutomationSecurityConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Root Spring configuration for the Automation API child context.
 * <p>
 * This context is created as a child of the management API's parent application context,
 * inheriting shared service beans (DomainService, EnvironmentService, IdentityProviderService,
 * PermissionService, etc.) while maintaining isolated security configuration.
 * <p>
 * Mirrors the APIM pattern where each API surface (Management, Portal, Automation)
 * gets its own {@code AnnotationConfigWebApplicationContext} with dedicated security.
 *
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@Configuration
@EnableWebSecurity
@Import(AutomationSecurityConfiguration.class)
public class AutomationConfiguration {
}
