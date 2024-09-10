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
package io.gravitee.am.repository.mongodb.gateway;

import com.mongodb.reactivestreams.client.AggregatePublisher;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.mongodb.common.AbstractMongoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.TimeUnit;

import org.springframework.core.env.Environment;

public abstract class AbstractGatewayMongoRepository extends AbstractMongoRepository {

    @Autowired
    @Qualifier("gatewayMongoTemplate")
    protected MongoDatabase mongoOperations;

    @Autowired
    private RepositoriesEnvironment environment;

    protected final <TResult> AggregatePublisher<TResult> withMaxTime(AggregatePublisher<TResult> query) {
        return query.maxTime(getMaxTimeInMs(), TimeUnit.MILLISECONDS);
    }

    protected final <TResult> FindPublisher<TResult> withMaxTime(FindPublisher<TResult> query) {
        return query.maxTime(getMaxTimeInMs(), TimeUnit.MILLISECONDS);
    }

    protected boolean getEnsureIndexOnStart() {
        var ensureIndexOnStartOld = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.ensureIndexOnStart", Boolean.class, true);
        return environment.getProperty(Scope.GATEWAY.getRepositoryPropertyKey() + ".mongodb.ensureIndexOnStart", Boolean.class, ensureIndexOnStartOld);
    }

    protected Integer getMaxTimeInMs() {
        var approvalExpirySecondsOld = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.cursorMaxTime", Integer.class, 60000);
        return environment.getProperty(Scope.GATEWAY.getRepositoryPropertyKey() + ".mongodb.cursorMaxTime", Integer.class, approvalExpirySecondsOld);
    }
}
