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
import io.gravitee.am.repository.management.AbstractManagementTest;
import org.bson.Document;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Duration;

import static io.gravitee.am.repository.mongodb.common.MongoIndexAssertions.assertIndex;
import static io.gravitee.am.repository.mongodb.common.MongoIndexAssertions.assertTtlIndex;
import static io.gravitee.am.repository.mongodb.common.MongoIndexAssertions.assertUniqueIndex;

/**
 * Asserts that the self-cleaning management collections actually carry the indexes their
 * repositories declare.
 * <p>
 * These three collections grow on their own - CIMD metadata is cached per client, leases are taken
 * on every scheduled action, and events are written on every configuration change - and each relies
 * on a TTL index to stay bounded. As elsewhere on MongoDB, nothing else removes their expired
 * records and a failed index creation is only logged, so the node starts up healthy either way.
 *
 * @author GraviteeSource Team
 */
public class ManagementStoreIndexTest extends AbstractManagementTest {

    private static final String CIMD_METADATA_DOCUMENTS = "cimd_metadata_documents";
    private static final String ACTION_LEASE = "cp_action_lease";
    private static final String EVENTS = "events";

    /** Default of {@code services.purge.events.retention.days}, applied by MongoEventRepository. */
    private static final Duration DEFAULT_EVENTS_RETENTION = Duration.ofDays(90);

    @Autowired
    @Qualifier("managementMongoTemplate")
    private MongoDatabase mongoDatabase;

    @Test
    public void cimdMetadataDocumentsShouldExpireAndStayUniquePerClient() {
        assertTtlIndex(mongoDatabase, CIMD_METADATA_DOCUMENTS, "ea1", "expiresAt", Duration.ZERO);
        assertUniqueIndex(mongoDatabase, CIMD_METADATA_DOCUMENTS, "di1ci1",
                new Document("domainId", 1).append("clientId", 1));
    }

    /**
     * The lease collection is what stops two nodes running the same scheduled action concurrently.
     * Without the TTL index a lease is never released, so the action stops running altogether;
     * without the unique index two nodes can hold the same lease at once.
     */
    @Test
    public void actionLeasesShouldExpireAndStayUniquePerAction() {
        assertTtlIndex(mongoDatabase, ACTION_LEASE, "expiry_ttl", "expiryDate", Duration.ZERO);
        assertUniqueIndex(mongoDatabase, ACTION_LEASE, "action_unique", new Document("action", 1));
    }

    /**
     * Events are the sync channel between management and the gateways. Their TTL is the configured
     * retention rather than immediate expiry, so this pins the retention actually applied to the
     * collection - not merely that some TTL index exists.
     */
    @Test
    public void eventsShouldExpireAfterTheConfiguredRetention() {
        assertTtlIndex(mongoDatabase, EVENTS, "e1", "createdAt", DEFAULT_EVENTS_RETENTION);
    }

    @Test
    public void eventsShouldCarryItsLookupIndexes() {
        assertIndex(mongoDatabase, EVENTS, "u1", new Document("updatedAt", 1));
        assertIndex(mongoDatabase, EVENTS, "u1dp1", new Document("updatedAt", 1).append("dataPlaneId", 1));
    }
}
