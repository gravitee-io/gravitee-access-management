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
package io.gravitee.am.repository.mongodb.management;

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.repository.management.AbstractConfigurationEncryptionTest;
import io.reactivex.rxjava3.core.Single;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author GraviteeSource Team
 */
public class MongoConfigurationEncryptionTest extends AbstractConfigurationEncryptionTest {

    @Autowired
    @Qualifier("managementMongoTemplate")
    private MongoDatabase mongoDatabase;

    @Override
    protected String storedConfiguration(String collection, String id) {
        Document document = Single.fromPublisher(mongoDatabase.getCollection(collection).find(eq("_id", id)).first()).blockingGet();
        return document.getString("configuration");
    }
}
