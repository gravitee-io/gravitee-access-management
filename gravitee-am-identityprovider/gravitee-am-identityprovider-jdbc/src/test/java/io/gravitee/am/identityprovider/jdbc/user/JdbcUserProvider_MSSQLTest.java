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
package io.gravitee.am.identityprovider.jdbc.user;

import io.gravitee.am.identityprovider.jdbc.configuration.JdbcAuthenticationProviderConfigurationTest_MSSQL;
import io.gravitee.am.identityprovider.jdbc.user.spring.JdbcUserProviderConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@ContextConfiguration(classes = { JdbcAuthenticationProviderConfigurationTest_MSSQL.class, JdbcUserProviderConfiguration.class }, loader = AnnotationConfigContextLoader.class)
public class JdbcUserProvider_MSSQLTest extends JdbcUserProvider_Test { }
