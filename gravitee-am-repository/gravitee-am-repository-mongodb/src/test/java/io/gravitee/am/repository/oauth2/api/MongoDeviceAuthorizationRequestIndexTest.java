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
package io.gravitee.am.repository.oauth2.api;

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import io.reactivex.rxjava3.core.Observable;
import org.bson.Document;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class MongoDeviceAuthorizationRequestIndexTest extends AbstractOAuthTest {

    @Autowired
    @Qualifier("oauth2MongoTemplate")
    private MongoDatabase mongoDatabase;

    @Test
    public void shouldCreateUniqueIndexOnUserCode() {
        Optional<Document> index = indexOn("user_code");

        assertTrue("no index on user_code", index.isPresent());
        assertEquals(Boolean.TRUE, index.get().getBoolean("unique"));
    }

    @Test
    public void shouldCreateTtlIndexOnExpireAt() {
        Optional<Document> index = indexOn("expire_at");

        assertTrue("no index on expire_at", index.isPresent());
        assertEquals(Long.valueOf(0L), Long.valueOf(index.get().get("expireAfterSeconds").toString()));
    }

    private Optional<Document> indexOn(String field) {
        List<Document> indexes = Observable
                .fromPublisher(mongoDatabase.getCollection("device_authorization_requests").listIndexes())
                .toList()
                .blockingGet();

        return indexes.stream()
                .filter(index -> index.get("key", Document.class).containsKey(field))
                .findFirst();
    }
}
