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
package io.gravitee.am.repository.mongodb.provider.impl;

import com.mongodb.reactivestreams.client.MongoClient;
import io.gravitee.am.repository.provider.ClientWrapper;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoClientWrapper implements ClientWrapper<MongoClient> {

    private final MongoClient client;

    private AtomicInteger reference = new AtomicInteger(0);

    public MongoClientWrapper(MongoClient client) {
        this.client = client;
    }

    @Override
    public MongoClient getClient() {
        this.reference.incrementAndGet();
        return this.client;
    }

    @Override
    public void releaseClient() {
        if (this.reference.decrementAndGet() <= 0) {
            this.shutdown();
        }
    }

    void shutdown() {
        this.reference.set(0);
        this.client.close();
    }

    int getClientReferenceValue() {
        return reference.get();
    }
}
