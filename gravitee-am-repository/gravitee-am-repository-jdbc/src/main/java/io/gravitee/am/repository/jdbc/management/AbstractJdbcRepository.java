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
package io.gravitee.am.repository.jdbc.management;

import com.github.dozermapper.core.DozerBeanMapperBuilder;
import com.github.dozermapper.core.Mapper;
import io.gravitee.am.repository.jdbc.management.api.model.mapper.LocalDateConverter;
import io.gravitee.am.repository.jdbc.common.dialect.DatabaseDialectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.r2dbc.core.DatabaseClient.GenericInsertSpec;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.transaction.ReactiveTransactionManager;

import java.util.Arrays;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractJdbcRepository {
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    protected DatabaseClient dbClient;

    @Autowired
    protected DatabaseDialectHelper databaseDialectHelper;

    @Autowired
    protected ReactiveTransactionManager tm;

    protected LocalDateConverter dateConverter = new LocalDateConverter();

    protected static final Mapper mapper = DozerBeanMapperBuilder.create().withMappingFiles(Arrays.asList("dozer.xml")).build();

    protected <T> GenericInsertSpec<Map<String, Object>> addQuotedField(GenericInsertSpec<Map<String, Object>> spec, String name, Object value, Class<T> type) {
        return value == null ? spec.nullValue(SqlIdentifier.quoted(name), type) : spec.value(SqlIdentifier.quoted(name), value);
    }

    protected <T> Map<SqlIdentifier, Object> addQuotedField(Map<SqlIdentifier, Object> spec, String name, Object value, Class<T> type) {
        spec.put(SqlIdentifier.quoted(name), value);
        return spec;
    }
}
