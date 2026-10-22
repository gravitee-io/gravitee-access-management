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

import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.IndexModel;
import org.bson.Document;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author GraviteeSource Team
 */
public class MongoIndexDeclarationsTest {

    private static final String DECLARATIONS = """
            { "collections": {
                "tokens": {
                  "indexes": [
                    { "name": "t1", "key": { "token": 1 } },
                    { "name": "d1c1", "key": { "domain": 1, "client": -1 }, "unique": true, "sparse": true },
                    { "name": "e1", "key": { "expire_at": 1 }, "expireAfterSeconds": 0 }
                  ],
                  "dropped": ["old1"]
                },
                "apps": {
                  "indexes": [
                    { "name": "n1_ci", "key": { "name": 1 }, "collation": { "locale": "en", "strength": 2 } },
                    { "name": "s1", "key": { "secret": 1 }, "unique": true, "partialFilterExpression": { "secret": { "$exists": true } } }
                  ]
                }
            } }
            """;

    @Test
    public void shouldParseEveryDeclaredIndexWithItsOptions() {
        MongoIndexDeclarations declarations = MongoIndexDeclarations.parse("test", DECLARATIONS);

        assertThat(declarations.collections()).containsOnlyKeys("tokens", "apps");
        assertThat(declarations.collections().get("tokens").dropped()).containsExactly("old1");

        IndexModel compound = index(declarations, "tokens", "d1c1");
        assertThat(compound.getKeys()).isEqualTo(new Document("domain", 1).append("client", -1));
        assertThat(compound.getOptions().isUnique()).isTrue();
        assertThat(compound.getOptions().isSparse()).isTrue();
        assertThat(index(declarations, "tokens", "e1").getOptions().getExpireAfter(TimeUnit.SECONDS)).isZero();
        assertThat(index(declarations, "apps", "n1_ci").getOptions().getCollation().getStrength()).isEqualTo(CollationStrength.SECONDARY);
        assertThat(index(declarations, "apps", "n1_ci").getOptions().getCollation().getLocale()).isEqualTo("en");
        assertThat(index(declarations, "apps", "s1").getOptions().getPartialFilterExpression())
                .isEqualTo(new Document("secret", new Document("$exists", true)));
    }

    @Test
    public void shouldConvertADurationIntoExpireAfterSeconds() {
        MongoIndexDeclarations declarations = MongoIndexDeclarations.parse("test", """
                { "collections": { "events": { "indexes": [ { "name": "e1", "key": { "createdAt": 1 }, "expireAfter": "90d" } ] } } }
                """);

        assertThat(index(declarations, "events", "e1").getOptions().getExpireAfter(TimeUnit.SECONDS)).isEqualTo(TimeUnit.DAYS.toSeconds(90));
    }

    @Test
    public void shouldResolvePlaceholdersBeforeParsing() {
        MongoIndexDeclarations declarations = MongoIndexDeclarations.load(getClass().getClassLoader(), "mongodb/indexes/placeholder.json",
                json -> json.replace("${retention}", "7"));

        assertThat(index(declarations, "events", "e1").getOptions().getExpireAfter(TimeUnit.SECONDS)).isEqualTo(TimeUnit.DAYS.toSeconds(7));
    }

    @Test
    public void shouldKeepTheSameChecksumWhenOnlyTheLayoutChanges() {
        String reordered = """
                { "collections": {
                  "apps": { "indexes": [
                      { "key": { "secret": 1 }, "partialFilterExpression": { "secret": { "$exists": true } }, "unique": true, "name": "s1" },
                      { "collation": { "locale": "en", "strength": 2 }, "key": { "name": 1 }, "name": "n1_ci" } ] },
                  "tokens": { "dropped": ["old1"], "indexes": [
                      { "name": "e1", "expireAfterSeconds": 0, "key": { "expire_at": 1 } },
                      { "name": "t1", "key": { "token": 1 } },
                      { "sparse": true, "unique": true, "name": "d1c1", "key": { "domain": 1, "client": -1 } } ] }
                } }
                """;

        assertThat(MongoIndexDeclarations.parse("a", reordered).checksum())
                .isEqualTo(MongoIndexDeclarations.parse("b", DECLARATIONS).checksum())
                .hasSize(12);
    }

    @Test
    public void shouldChangeTheChecksumWhenAnIndexChanges() {
        String changed = DECLARATIONS.replace("\"expireAfterSeconds\": 0", "\"expireAfterSeconds\": 60");

        assertThat(MongoIndexDeclarations.parse("test", changed).checksum())
                .isNotEqualTo(MongoIndexDeclarations.parse("test", DECLARATIONS).checksum());
    }

    @Test
    public void shouldChangeTheChecksumWhenTheKeyOrderChanges() {
        String changed = DECLARATIONS.replace("{ \"domain\": 1, \"client\": -1 }", "{ \"client\": -1, \"domain\": 1 }");

        assertThat(MongoIndexDeclarations.parse("test", changed).checksum())
                .isNotEqualTo(MongoIndexDeclarations.parse("test", DECLARATIONS).checksum());
    }

    @Test
    public void shouldChangeTheChecksumWhenARetiredIndexIsAdded() {
        String changed = DECLARATIONS.replace("[\"old1\"]", "[\"old1\", \"old2\"]");

        assertThat(MongoIndexDeclarations.parse("test", changed).checksum())
                .isNotEqualTo(MongoIndexDeclarations.parse("test", DECLARATIONS).checksum());
    }

    @Test
    public void shouldRejectAnUnknownOption() {
        assertInvalid("""
                { "collections": { "c": { "indexes": [ { "name": "t1", "key": { "t": 1 }, "background": true } ] } } }
                """, "unknown field 'background'");
    }

    @Test
    public void shouldRejectAnIndexWithoutName() {
        assertInvalid("""
                { "collections": { "c": { "indexes": [ { "key": { "t": 1 } } ] } } }
                """, "has no name");
    }

    @Test
    public void shouldRejectAnIndexWithoutKey() {
        assertInvalid("""
                { "collections": { "c": { "indexes": [ { "name": "t1" } ] } } }
                """, "has no key");
    }

    @Test
    public void shouldRejectAnIndexDeclaredTwice() {
        assertInvalid("""
                { "collections": { "c": { "indexes": [ { "name": "t1", "key": { "t": 1 } }, { "name": "t1", "key": { "u": 1 } } ] } } }
                """, "declared twice");
    }

    @Test
    public void shouldRejectAnIndexBothDeclaredAndDropped() {
        assertInvalid("""
                { "collections": { "c": { "indexes": [ { "name": "t1", "key": { "t": 1 } } ], "dropped": ["t1"] } } }
                """, "both declared and dropped");
    }

    @Test
    public void shouldRejectDroppingTheIdIndex() {
        assertInvalid("""
                { "collections": { "c": { "dropped": ["_id_"] } } }
                """, "cannot be dropped");
    }

    @Test
    public void shouldRejectAnInvalidDuration() {
        assertInvalid("""
                { "collections": { "c": { "indexes": [ { "name": "e1", "key": { "e": 1 }, "expireAfter": "90 days" } ] } } }
                """, "is not a duration");
    }

    @Test
    public void shouldRejectTwoExpirations() {
        assertInvalid("""
                { "collections": { "c": { "indexes": [ { "name": "e1", "key": { "e": 1 }, "expireAfter": "1d", "expireAfterSeconds": 1 } ] } } }
                """, "both expireAfterSeconds and expireAfter");
    }

    @Test
    public void shouldRejectAnUnsupportedCollationOption() {
        assertInvalid("""
                { "collections": { "c": { "indexes": [ { "name": "n1", "key": { "n": 1 }, "collation": { "locale": "en", "numericOrdering": true } } ] } } }
                """, "unknown field 'numericOrdering'");
    }

    @Test
    public void shouldRejectAMissingResource() {
        assertThatThrownBy(() -> MongoIndexDeclarations.load(getClass().getClassLoader(), "mongodb/indexes/missing.json", json -> json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    private static void assertInvalid(String json, String reason) {
        assertThatThrownBy(() -> MongoIndexDeclarations.parse("broken.json", json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("broken.json")
                .hasMessageContaining(reason);
    }

    private static IndexModel index(MongoIndexDeclarations declarations, String collection, String name) {
        return declarations.collections().get(collection).indexes().stream()
                .filter(model -> name.equals(model.getOptions().getName()))
                .findFirst()
                .orElseThrow();
    }
}
