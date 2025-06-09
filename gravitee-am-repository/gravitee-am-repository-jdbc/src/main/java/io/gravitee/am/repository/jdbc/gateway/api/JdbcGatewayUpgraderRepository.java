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
package io.gravitee.am.repository.jdbc.gateway.api;

import io.gravitee.am.repository.jdbc.provider.common.AbstractJdbcUpgraderRepository;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import static io.gravitee.am.repository.upgrader.UpgraderTargets.GATEWAY_UPGRADER_TARGET;

@Repository
@Qualifier("gatewayUpgraderRepository")
public class JdbcGatewayUpgraderRepository extends AbstractJdbcUpgraderRepository implements UpgraderRepository {

    @Autowired
    private DatabaseClient databaseClient;

    @Override
    protected String getTableName() {
        return GATEWAY_UPGRADER_TARGET;
    }

    @Override
    protected DatabaseClient getDatabaseClient() {
        return databaseClient;
    }

}
