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
package io.gravitee.am.repository.jdbc.common;

import io.r2dbc.spi.ConnectionFactoryOptions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.MySQLR2DBCDatabaseContainer;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MysqlR2DBCContainer implements R2dbcDatabaseContainer {
    private MySQLContainer dbContainer;
    private MySQLR2DBCDatabaseContainer r2dbcContainer;

    public MysqlR2DBCContainer(MySQLContainer dbContainer) {
        this.dbContainer = dbContainer;
        this.r2dbcContainer = new MySQLR2DBCDatabaseContainer(dbContainer);
    }

    public void start() {
        r2dbcContainer.start();
    }

    public void stop() {
        r2dbcContainer.stop();
    }

    public ConnectionFactoryOptions getOptions() {
        return MySQLR2DBCDatabaseContainer.getOptions(dbContainer);
    }
}
