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
package io.gravitee.am.repository.jdbc.provider;

import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.core.ReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;

/**
 * This interface is used to provide Spring Data bean to the Reporter instance in order to avoid
 * LinkageError under Java 17
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface R2DBCSpringBeanAccessor {
    ReactiveTransactionManager reactiveTransactionManager();
    R2dbcDialect dialectDatabase();
    DatabaseClient databaseClient();
    R2dbcEntityTemplate r2dbcEntityTemplate();
    R2dbcMappingContext r2dbcMappingContext();
    ReactiveDataAccessStrategy reactiveDataAccessStrategy();
    MappingR2dbcConverter r2dbcConverter();
    R2dbcCustomConversions r2dbcCustomConversions();
}
