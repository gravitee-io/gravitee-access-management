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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.repository.management.AbstractConfigurationEncryptionTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

/**
 * @author GraviteeSource Team
 */
public class JdbcConfigurationEncryptionTest extends AbstractConfigurationEncryptionTest {

    @Autowired
    private R2dbcEntityTemplate template;

    @Override
    protected String storedConfiguration(String table, String id) {
        return template.getDatabaseClient()
                .sql("SELECT configuration FROM " + table + " WHERE id = :id")
                .bind("id", id)
                .map(row -> row.get(0, String.class))
                .one()
                .block();
    }
}
