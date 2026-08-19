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

import com.mongodb.MongoCommandException;
import com.mongodb.MongoNamespace;
import com.mongodb.ServerAddress;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.ListIndexesPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.reactivex.rxjava3.core.Flowable;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Subscriber;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
public class MongoIndexManagerContractTest {

    private static final String COLLECTION = "unreadable";

    @Before
    public void resetReport() {
        MongoIndexReport.clear();
    }

    @Test
    public void everyDeclaredIndexIsReportedWhenTheCatalogCannotBeRead() {
        MongoCollection<Document> collection = collectionThatCannotBeReconciled();

        // completing at all is the assertion: an error here reaches callers that cannot handle one
        MongoIndexManager.ensureIndexes(collection, List.of(
                        new IndexModel(new Document("token", 1), new IndexOptions().name("t1")),
                        new IndexModel(new Document("subject", 1), new IndexOptions().name("s1"))))
                .blockingAwait();

        assertThat(MongoIndexReport.failures())
                .extracting(MongoIndexReport.IndexOutcome::index)
                .as("a reconciliation that could not run leaves every declared index unaccounted for")
                .containsExactlyInAnyOrder("t1", "s1");
    }

    @Test
    public void noDeclarationsIsNotAFailure() {
        MongoCollection<Document> collection = collectionThatCannotBeReconciled();

        MongoIndexManager.ensureIndexes(collection, List.of()).blockingAwait();

        assertThat(MongoIndexReport.outcomes()).isEmpty();
    }

    /** Rejects the batch, then fails the catalog read that reconciliation depends on. */
    @SuppressWarnings("unchecked")
    private static MongoCollection<Document> collectionThatCannotBeReconciled() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(collection.getNamespace()).thenReturn(new MongoNamespace("test", COLLECTION));
        when(collection.createIndexes(anyList())).thenReturn(Flowable.error(indexOptionsConflict()));

        ListIndexesPublisher<Document> unreadable = mock(ListIndexesPublisher.class);
        doAnswer(invocation -> {
            // delegate so the subscriber still sees a well-formed onSubscribe/onError sequence
            Flowable.<Document>error(new IllegalStateException("catalog unavailable"))
                    .subscribe((Subscriber<Document>) invocation.getArgument(0));
            return null;
        }).when(unreadable).subscribe(any());
        when(collection.listIndexes()).thenReturn(unreadable);
        return collection;
    }

    /** A conflict rather than a transient error, so reconciliation is reached without a retry. */
    private static MongoCommandException indexOptionsConflict() {
        BsonDocument response = new BsonDocument("ok", new BsonInt32(0))
                .append("code", new BsonInt32(85))
                .append("codeName", new BsonString("IndexOptionsConflict"))
                .append("errmsg", new BsonString("an index over this key already exists"));
        return new MongoCommandException(response, new ServerAddress());
    }
}
