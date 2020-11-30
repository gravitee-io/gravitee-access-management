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
package io.gravitee.am.repository.mongodb.common;

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.repository.RepositoriesTestInitializer;
import io.reactivex.Observable;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Service
public class MongodbRepositoriesTestInitializer implements RepositoriesTestInitializer {

    @Autowired
    private MongoDatabase mongoDatabase;

    @Override
    public void before(Class testClass) throws Exception {
        Thread.sleep(1000l);
    }

    @Override
    public void after(Class testClass) throws Exception {
        Observable.fromPublisher(mongoDatabase.listCollectionNames())
                .flatMap(collectionName -> Observable.fromPublisher(mongoDatabase.getCollection(collectionName).deleteMany(new Document())))
                .blockingSubscribe();
    }
}
