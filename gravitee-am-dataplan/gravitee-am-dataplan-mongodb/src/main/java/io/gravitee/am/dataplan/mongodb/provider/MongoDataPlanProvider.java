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

package io.gravitee.am.dataplan.mongodb.provider;


import com.mongodb.reactivestreams.client.MongoClient;
import io.gravitee.am.dataplan.api.DataPlanDescription;
import io.gravitee.am.dataplan.api.provider.DataPlanProvider;
import io.gravitee.am.dataplan.api.repository.DataPlanPOCRepository;
import io.gravitee.am.dataplan.mongodb.repository.MongoDataPlanPOCRepository;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoDataPlanProvider implements DataPlanProvider, InitializingBean {

    @Autowired
    private ConnectionProvider connectionProvider;

    @Autowired
    private DataPlanDescription description;

    private ClientWrapper<MongoClient> clientWrapper;

    private DataPlanPOCRepository pocRepository;

    @Override
    public void afterPropertiesSet() throws Exception {
        // TODO test connProvider type to allow mixing type (mongo, jdbc...)
        clientWrapper = connectionProvider.getClientWrapperFromPrefix(description.settingsPrefix());
        final MongoClient mongo = clientWrapper.getClient();
        final var mongoDb = mongo.getDatabase(clientWrapper.databaseName());
        this.pocRepository = new MongoDataPlanPOCRepository(mongoDb);
    }

    @Override
    public DataPlanPOCRepository getDataPlanPOCRepository() {
        return pocRepository;
    }

    @Override
    public void stop() {
        if (clientWrapper != null) {
            clientWrapper.releaseClient();
        }
    }
}
