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

package io.gravitee.am.dataplane.mongodb.repository;


import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.AggregatePublisher;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.mongodb.common.FilterCriteriaParser;
import io.gravitee.am.repository.mongodb.common.MongoUtils;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractDataPlaneMongoRepository {

    @Autowired
    @Qualifier("dataPlaneMongoDatabase")
    protected MongoDatabase mongoDatabase;

    @Value("${gateway.mongodb.cursorMaxTime:60000}")
    private int cursorMaxTimeInMs;

    @Autowired
    private RepositoriesEnvironment environment;

    @Autowired
    protected FilterCriteriaParser filterCriteriaParser;

    protected final boolean getEnsureIndexOnStart() {
        var ensureIndexOnStartOAuth2Old = environment.getProperty(Scope.OAUTH2.getRepositoryPropertyKey() + ".mongodb.ensureIndexOnStart", Boolean.class, true);
        return environment.getProperty(Scope.GATEWAY.getRepositoryPropertyKey() + ".mongodb.ensureIndexOnStart", Boolean.class, ensureIndexOnStartOAuth2Old);
    }

    protected void createIndex(MongoCollection<?> collection, Map<Document, IndexOptions> indexes) {
        MongoUtils.createIndex(collection, indexes, getEnsureIndexOnStart());
    }

    protected final <TResult> AggregatePublisher<TResult> withMaxTime(AggregatePublisher<TResult> query) {
        return query.maxTime(this.cursorMaxTimeInMs, TimeUnit.MILLISECONDS);
    }

    protected final <TResult> FindPublisher<TResult> withMaxTime(FindPublisher<TResult> query) {
        return query.maxTime(this.cursorMaxTimeInMs, TimeUnit.MILLISECONDS);
    }

    protected final CountOptions countOptions() {
        return new CountOptions().maxTime(cursorMaxTimeInMs, TimeUnit.MILLISECONDS);
    }
}
