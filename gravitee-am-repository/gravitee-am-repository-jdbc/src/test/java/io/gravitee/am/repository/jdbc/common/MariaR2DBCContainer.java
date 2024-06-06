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
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MariaDBR2DBCDatabaseContainer;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MariaR2DBCContainer implements R2dbcDatabaseContainer {
    private MariaDBContainer dbContainer;
    private MariaDBR2DBCDatabaseContainer r2dbcContainer;

    public MariaR2DBCContainer(MariaDBContainer dbContainer) {
        this.dbContainer = dbContainer;
        this.r2dbcContainer = new MariaDBR2DBCDatabaseContainer(dbContainer);
    }

    public void start() {
        r2dbcContainer.start();
    }

    public void stop() {
        r2dbcContainer.stop();
    }

    public ConnectionFactoryOptions getOptions() {
        return MariaDBR2DBCDatabaseContainer.getOptions(dbContainer);
    }

    @Override
    public String getJdbcUrl() {
        return dbContainer.getJdbcUrl();
    }
}
