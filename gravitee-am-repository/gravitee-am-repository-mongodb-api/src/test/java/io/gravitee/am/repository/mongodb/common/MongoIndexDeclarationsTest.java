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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author GraviteeSource Team
 */
public class MongoIndexDeclarationsTest {

    private static final String DECLARATIONS = """
            { "changesets": [
              { "id": "4.14.0",
                "create": {
                  "tokens": [
                    { "name": "t1", "key": { "token": 1 } },
                    { "name": "d1c1", "key": { "domain": 1, "client": -1 }, "unique": true, "sparse": true },
                    { "name": "e1", "key": { "expire_at": 1 }, "expireAfterSeconds": 0 }
                  ],
                  "apps": [
                    { "name": "n1_ci", "key": { "name": 1 }, "collation": { "locale": "en", "strength": 2 } },
                    { "name": "s1", "key": { "secret": 1 }, "unique": true, "partialFilterExpression": { "secret": { "$exists": true } } }
                  ]
                },
                "drop": { "tokens": ["old1"] } }
            ] }
            """;

    @Test
    public void shouldParseEveryCreatedIndexWithItsOptions() {
        MongoIndexDeclarations declarations = MongoIndexDeclarations.parse("test", DECLARATIONS);

        assertThat(declarations.indexesByCollection()).containsOnlyKeys("tokens", "apps");
        assertThat(declarations.indexesByCollection().get("tokens").dropped()).containsExactly("old1");

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
    public void shouldReplayTheChangesetsInOrder() {
        MongoIndexDeclarations declarations = MongoIndexDeclarations.parse("test", """
                { "changesets": [
                  { "id": "4.14.0", "create": { "c": [ { "name": "a1", "key": { "a": 1 } }, { "name": "b1", "key": { "b": 1 } } ] } },
                  { "id": "4.15.0", "drop": { "c": ["a1", "legacy"] }, "create": { "c": [ { "name": "c1", "key": { "c": 1 } } ] } }
                ] }
                """);

        assertThat(declarations.indexesByCollection().get("c").indexes()).extracting(model -> model.getOptions().getName()).containsExactly("b1", "c1");
        assertThat(declarations.indexesByCollection().get("c").dropped()).containsExactly("a1", "legacy");
        assertThat(declarations.changesets()).extracting(MongoIndexDeclarations.Changeset::id).containsExactly("4.14.0", "4.15.0");
    }

    @Test
    public void shouldGiveEachChangesetItsOwnIndexesAndDrops() {
        MongoIndexDeclarations declarations = MongoIndexDeclarations.parse("test", """
                { "changesets": [
                  { "id": "a", "create": { "c": [ { "name": "a1", "key": { "a": 1 } } ], "d": [ { "name": "d1", "key": { "d": 1 } } ] } },
                  { "id": "b", "drop": { "c": ["a1"] }, "create": { "c": [ { "name": "b1", "key": { "b": 1 } } ] } }
                ] }
                """);

        Map<String, MongoIndexDeclarations.CollectionIndexes> first = declarations.changesets().getFirst().indexesByCollection();
        assertThat(first).containsOnlyKeys("c", "d");
        assertThat(first.get("c").indexes()).extracting(model -> model.getOptions().getName()).containsExactly("a1");
        assertThat(first.get("c").dropped()).isEmpty();

        Map<String, MongoIndexDeclarations.CollectionIndexes> second = declarations.changesets().getLast().indexesByCollection();
        assertThat(second).containsOnlyKeys("c");
        assertThat(second.get("c").indexes()).extracting(model -> model.getOptions().getName()).containsExactly("b1");
        assertThat(second.get("c").dropped()).containsExactly("a1");
    }

    @Test
    public void shouldRedefineAnIndexDroppedByAnEarlierChangeset() {
        MongoIndexDeclarations declarations = MongoIndexDeclarations.parse("test", """
                { "changesets": [
                  { "id": "4.14.0", "create": { "c": [ { "name": "a1", "key": { "a": 1 } } ] } },
                  { "id": "4.15.0", "drop": { "c": ["a1"] } },
                  { "id": "4.16.0", "create": { "c": [ { "name": "a1", "key": { "a": 1 }, "unique": true } ] } }
                ] }
                """);

        assertThat(index(declarations, "c", "a1").getOptions().isUnique()).isTrue();
        assertThat(declarations.indexesByCollection().get("c").dropped()).isEmpty();
    }

    @Test
    public void shouldKeepACollectionWhoseIndexesAreAllDropped() {
        MongoIndexDeclarations declarations = MongoIndexDeclarations.parse("test", """
                { "changesets": [
                  { "id": "4.14.0", "create": { "c": [ { "name": "a1", "key": { "a": 1 } } ] } },
                  { "id": "4.15.0", "drop": { "c": ["a1"] } }
                ] }
                """);

        assertThat(declarations.indexesByCollection().get("c").indexes()).isEmpty();
        assertThat(declarations.indexesByCollection().get("c").dropped()).containsExactly("a1");
    }

    @Test
    public void shouldConvertADurationIntoExpireAfterSeconds() {
        MongoIndexDeclarations declarations = MongoIndexDeclarations.parse("test", """
                { "changesets": [ { "id": "1", "create": { "events": [ { "name": "e1", "key": { "createdAt": 1 }, "expireAfter": "90d" } ] } } ] }
                """);

        assertThat(index(declarations, "events", "e1").getOptions().getExpireAfter(TimeUnit.SECONDS)).isEqualTo(TimeUnit.DAYS.toSeconds(90));
    }

    @Test
    public void shouldKeepTheSameChangesetChecksumWhenOnlyTheLayoutChanges() {
        String reordered = """
                { "changesets": [
                  { "drop": { "tokens": ["old1"] },
                    "create": {
                      "apps": [
                        { "key": { "secret": 1 }, "partialFilterExpression": { "secret": { "$exists": true } }, "unique": true, "name": "s1" },
                        { "collation": { "locale": "en", "strength": 2 }, "key": { "name": 1 }, "name": "n1_ci" } ],
                      "tokens": [
                        { "name": "e1", "expireAfterSeconds": 0, "key": { "expire_at": 1 } },
                        { "name": "t1", "key": { "token": 1 } },
                        { "sparse": true, "unique": true, "name": "d1c1", "key": { "domain": 1, "client": -1 } } ] },
                    "id": "4.14.0" }
                ] }
                """;

        assertThat(checksum(reordered)).isEqualTo(checksum(DECLARATIONS)).hasSize(12);
    }

    @Test
    public void shouldChangeTheChangesetChecksumWhenAnIndexChanges() {
        assertThat(checksum(DECLARATIONS.replace("\"expireAfterSeconds\": 0", "\"expireAfterSeconds\": 60")))
                .isNotEqualTo(checksum(DECLARATIONS));
    }

    @Test
    public void shouldChangeTheChangesetChecksumWhenTheKeyOrderChanges() {
        assertThat(checksum(DECLARATIONS.replace("{ \"domain\": 1, \"client\": -1 }", "{ \"client\": -1, \"domain\": 1 }")))
                .isNotEqualTo(checksum(DECLARATIONS));
    }

    @Test
    public void shouldChangeTheChangesetChecksumWhenADropIsAdded() {
        assertThat(checksum(DECLARATIONS.replace("[\"old1\"]", "[\"old1\", \"old2\"]")))
                .isNotEqualTo(checksum(DECLARATIONS));
    }

    @Test
    public void shouldChangeTheChangesetChecksumWhenTheIdChanges() {
        assertThat(checksum(DECLARATIONS.replace("4.14.0", "4.15.0"))).isNotEqualTo(checksum(DECLARATIONS));
    }

    @Test
    public void shouldRejectMissingChangesets() {
        assertInvalid("""
                { "changesets": [] }
                """, "'changesets' is missing or empty");
    }

    @Test
    public void shouldRejectAnUnknownRootField() {
        assertInvalid("""
                { "collections": {} }
                """, "unknown field 'collections' in root");
    }

    @Test
    public void shouldRejectAChangesetWithoutId() {
        assertInvalid("""
                { "changesets": [ { "create": { "c": [ { "name": "t1", "key": { "t": 1 } } ] } } ] }
                """, "a changeset has no id");
    }

    @Test
    public void shouldRejectAChangesetDeclaredTwice() {
        assertInvalid("""
                { "changesets": [
                  { "id": "1", "create": { "c": [ { "name": "t1", "key": { "t": 1 } } ] } },
                  { "id": "1", "create": { "c": [ { "name": "u1", "key": { "u": 1 } } ] } } ] }
                """, "changeset 1 is declared twice");
    }

    @Test
    public void shouldRejectAnUnknownChangesetField() {
        assertInvalid("""
                { "changesets": [ { "id": "1", "indexes": { "c": [ { "name": "t1", "key": { "t": 1 } } ] } } ] }
                """, "unknown field 'indexes' in changeset 1");
    }

    @Test
    public void shouldRejectAnEmptyChangeset() {
        assertInvalid("""
                { "changesets": [ { "id": "1", "create": { "c": [] } } ] }
                """, "neither creates nor drops");
    }

    @Test
    public void shouldRejectAnUnknownOption() {
        assertInvalid("""
                { "changesets": [ { "id": "1", "create": { "c": [ { "name": "t1", "key": { "t": 1 }, "background": true } ] } } ] }
                """, "unknown field 'background'");
    }

    @Test
    public void shouldRejectAnIndexWithoutName() {
        assertInvalid("""
                { "changesets": [ { "id": "1", "create": { "c": [ { "key": { "t": 1 } } ] } } ] }
                """, "has no name");
    }

    @Test
    public void shouldRejectAnIndexWithoutKey() {
        assertInvalid("""
                { "changesets": [ { "id": "1", "create": { "c": [ { "name": "t1" } ] } } ] }
                """, "has no key");
    }

    @Test
    public void shouldRejectAnIndexCreatedTwiceInAChangeset() {
        assertInvalid("""
                { "changesets": [ { "id": "1", "create": { "c": [ { "name": "t1", "key": { "t": 1 } }, { "name": "t1", "key": { "u": 1 } } ] } } ] }
                """, "creates index t1 of c twice");
    }

    @Test
    public void shouldRejectAnIndexCreatedAndDroppedInAChangeset() {
        assertInvalid("""
                { "changesets": [ { "id": "1", "create": { "c": [ { "name": "t1", "key": { "t": 1 } } ] }, "drop": { "c": ["t1"] } } ] }
                """, "both creates and drops index t1 of c");
    }

    @Test
    public void shouldRejectRecreatingAnIndexThatWasNotDropped() {
        assertInvalid("""
                { "changesets": [
                  { "id": "1", "create": { "c": [ { "name": "t1", "key": { "t": 1 } } ] } },
                  { "id": "2", "create": { "c": [ { "name": "t1", "key": { "t": 1 }, "unique": true } ] } } ] }
                """, "an earlier changeset already created without dropping it");
    }

    @Test
    public void shouldRejectDroppingTheIdIndex() {
        assertInvalid("""
                { "changesets": [ { "id": "1", "drop": { "c": ["_id_"] } } ] }
                """, "cannot be dropped");
    }

    @Test
    public void shouldRejectADropWithoutName() {
        assertInvalid("""
                { "changesets": [ { "id": "1", "drop": { "c": [1] } } ] }
                """, "drops an index of c without a name");
    }

    @Test
    public void shouldRejectAnInvalidDuration() {
        assertInvalid("""
                { "changesets": [ { "id": "1", "create": { "c": [ { "name": "e1", "key": { "e": 1 }, "expireAfter": "90 days" } ] } } ] }
                """, "is not a duration");
    }

    @Test
    public void shouldRejectTwoExpirations() {
        assertInvalid("""
                { "changesets": [ { "id": "1", "create": { "c": [ { "name": "e1", "key": { "e": 1 }, "expireAfter": "1d", "expireAfterSeconds": 1 } ] } } ] }
                """, "both expireAfterSeconds and expireAfter");
    }

    @Test
    public void shouldRejectAnUnsupportedCollationOption() {
        assertInvalid("""
                { "changesets": [ { "id": "1", "create": { "c": [ { "name": "n1", "key": { "n": 1 }, "collation": { "locale": "en", "numericOrdering": true } } ] } } ] }
                """, "unknown field 'numericOrdering'");
    }

    @Test
    public void shouldRejectACollationWithoutLocale() {
        assertInvalid("""
                { "changesets": [ { "id": "1", "create": { "c": [ { "name": "n1", "key": { "n": 1 }, "collation": { "strength": 2 } } ] } } ] }
                """, "collation of c.n1 has no locale");
    }

    @Test
    public void shouldRejectAMissingResource() {
        assertThatThrownBy(() -> MongoIndexDeclarations.load(getClass().getClassLoader(), "mongodb/indexes/missing.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    private static void assertInvalid(String json, String reason) {
        assertThatThrownBy(() -> MongoIndexDeclarations.parse("broken.json", json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("broken.json")
                .hasMessageContaining(reason);
    }

    private static String checksum(String json) {
        return MongoIndexDeclarations.parse("test", json).changesets().getFirst().checksum();
    }

    private static IndexModel index(MongoIndexDeclarations declarations, String collection, String name) {
        return declarations.indexesByCollection().get(collection).indexes().stream()
                .filter(model -> name.equals(model.getOptions().getName()))
                .findFirst()
                .orElseThrow();
    }
}
