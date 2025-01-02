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
package io.gravitee.am.dataplane.mongodb.provider;

import com.mongodb.reactivestreams.client.MongoClient;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class MongoDataPlaneProvider implements DataPlaneProvider, InitializingBean {

    @Autowired
    private ConnectionProvider connectionProvider;

    @Autowired
    private DataPlaneDescription dataPlaneDescription;

    private ClientWrapper<MongoClient> clientWrapper;

    @Override
    public void afterPropertiesSet() throws Exception {
        clientWrapper = connectionProvider.getClientWrapperFromPrefix(dataPlaneDescription.propertiesBase());
        final MongoClient mongo = clientWrapper.getClient();
        final var mongoDb = mongo.getDatabase(clientWrapper.getDatabaseName());
        log.info("Init " + mongoDb);
    }

    @Override
    public void stop() {
        if (clientWrapper != null) {
            clientWrapper.releaseClient();
        }
    }


}
