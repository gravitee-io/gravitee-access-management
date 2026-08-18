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
package io.gravitee.am.repository.mongodb.oauth2;

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import org.bson.Document;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Duration;
import java.util.List;

import static io.gravitee.am.repository.mongodb.common.MongoIndexAssertions.assertIndex;
import static io.gravitee.am.repository.mongodb.common.MongoIndexAssertions.assertTtlIndex;

/**
 * Asserts that the OAuth2 token stores actually carry the indexes their repositories declare.
 * <p>
 * The TTL indexes matter most. On MongoDB they are the sole mechanism removing expired records from
 * these collections: {@code GatewayPurgeServiceConfiguration} enables the scheduled purge service
 * only when the gateway or oauth2 repository type is {@code jdbc}, and none of the Mongo token
 * repositories implement a purge. Losing one means expired tokens accumulate forever with no second
 * line of defense and no error anywhere.
 *
 * @author GraviteeSource Team
 */
public class OAuth2TokenStoreIndexTest extends AbstractOAuthTest {

    private static final String TTL_INDEX = "e1";
    private static final String EXPIRE_AT = "expire_at";

    /** Every collection holding a record with an expiry, and which must therefore self-clean. */
    private static final List<String> EXPIRING_COLLECTIONS = List.of(
            "access_tokens",
            "refresh_tokens",
            "authorization_codes",
            "tokens",
            "pushed_authorization_requests",
            "request_objects",
            "ciba_auth_requests");

    @Autowired
    @Qualifier("oauth2MongoTemplate")
    private MongoDatabase mongoDatabase;

    @Test
    public void everyExpiringCollectionShouldCarryATtlIndexOnExpireAt() {
        EXPIRING_COLLECTIONS.forEach(collection ->
                assertTtlIndex(mongoDatabase, collection, TTL_INDEX, EXPIRE_AT, Duration.ZERO));
    }

    @Test
    public void accessTokensShouldCarryItsLookupIndexes() {
        assertIndex(mongoDatabase, "access_tokens", "t1", new Document("token", 1));
        assertIndex(mongoDatabase, "access_tokens", "c1", new Document("client", 1));
        assertIndex(mongoDatabase, "access_tokens", "ac1", new Document("authorization_code", 1));
        assertIndex(mongoDatabase, "access_tokens", "s1", new Document("subject", 1));
        assertIndex(mongoDatabase, "access_tokens", "d1c1s1",
                new Document("domain", 1).append("client", 1).append("subject", 1));
    }

    @Test
    public void refreshTokensShouldCarryItsLookupIndexes() {
        assertIndex(mongoDatabase, "refresh_tokens", "t1", new Document("token", 1));
        assertIndex(mongoDatabase, "refresh_tokens", "s1", new Document("subject", 1));
        assertIndex(mongoDatabase, "refresh_tokens", "d1c1s1",
                new Document("domain", 1).append("client", 1).append("subject", 1));
    }

    @Test
    public void authorizationCodesShouldCarryItsLookupIndexes() {
        assertIndex(mongoDatabase, "authorization_codes", "c1", new Document("code", 1));
        assertIndex(mongoDatabase, "authorization_codes", "t1", new Document("transactionId", 1));
    }

    /**
     * The unified token collection backs revocation, which walks the parent/child token graph - so
     * these indexes carry the revocation path as well as ordinary lookups.
     */
    @Test
    public void unifiedTokensShouldCarryItsLookupIndexes() {
        assertIndex(mongoDatabase, "tokens", "t1", new Document("jti", 1));
        assertIndex(mongoDatabase, "tokens", "c1", new Document("client", 1));
        assertIndex(mongoDatabase, "tokens", "ac1", new Document("authorization_code", 1));
        assertIndex(mongoDatabase, "tokens", "s1", new Document("subject", 1));
        assertIndex(mongoDatabase, "tokens", "pj1", new Document("parent_jtis", 1));
        assertIndex(mongoDatabase, "tokens", "d1c1s1",
                new Document("domain", 1).append("client", 1).append("subject", 1));
        assertIndex(mongoDatabase, "tokens", "d1s1", new Document("domain", 1).append("subject", 1));
    }
}
