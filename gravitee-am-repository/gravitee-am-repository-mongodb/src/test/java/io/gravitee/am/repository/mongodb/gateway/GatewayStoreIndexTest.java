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

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.repository.gateway.AbstractGatewayTest;
import org.bson.Document;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Duration;

import static io.gravitee.am.repository.mongodb.common.MongoIndexAssertions.assertIndex;
import static io.gravitee.am.repository.mongodb.common.MongoIndexAssertions.assertTtlIndex;
import static io.gravitee.am.repository.mongodb.common.MongoIndexAssertions.assertUniqueIndex;

public class GatewayStoreIndexTest extends AbstractGatewayTest {

    @Autowired
    @Qualifier("gatewayMongoTemplate")
    private MongoDatabase mongoDatabase;

    @Test
    public void shouldIndexAuthenticationFlowContexts() {
        assertIndex(mongoDatabase, "auth_flow_ctx", "t1v_1", new Document("transactionId", 1).append("version", -1));
        assertTtlIndex(mongoDatabase, "auth_flow_ctx", "e1", "expire_at", Duration.ZERO);
    }

    @Test
    public void shouldIndexActionLeases() {
        assertUniqueIndex(mongoDatabase, "dp_action_lease", "action_unique", new Document("action", 1));
        assertTtlIndex(mongoDatabase, "dp_action_lease", "expiry_ttl", "expiryDate", Duration.ZERO);
    }

    @Test
    public void shouldIndexEmailStaging() {
        assertIndex(mongoDatabase, "dp_email_staging", "updated_at_idx",
                new Document("referenceType", 1).append("referenceId", 1).append("updatedAt", 1));
    }

    @Test
    public void shouldIndexRateLimitsAndVerifyAttempts() {
        Document key = new Document("userId", 1).append("client", 1).append("factorId", 1);
        assertIndex(mongoDatabase, "rate_limit", "u1c1f1", key);
        assertIndex(mongoDatabase, "verify_attempt", "u1c1f1", key);
    }
}
