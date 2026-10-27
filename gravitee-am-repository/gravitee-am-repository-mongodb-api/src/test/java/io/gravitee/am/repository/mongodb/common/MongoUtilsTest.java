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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.gravitee.am.repository.mongodb.common.MongoUtils.caseInsensitiveExactPattern;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.wildcardPrefixPattern;
import static org.assertj.core.api.Assertions.assertThat;

class MongoUtilsTest {

    @Test
    void shouldMatchWildcardPrefixIgnoringCase() {
        var pattern = wildcardPrefixPattern("john*doe*");

        assertThat(pattern.pattern()).isEqualTo("^john.*doe.*");
        assertThat(pattern.matcher("JOHN.smith-doe@mail.com").find()).isTrue();
        assertThat(pattern.matcher("mr.john.doe").find()).isFalse();
    }

    @Test
    void shouldCollapseConsecutiveWildcards() {
        assertThat(wildcardPrefixPattern("a***b*").pattern()).isEqualTo("^a.*b.*");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a(*", "a[*", "a{*", "a)*", "a\\*", "a|*", "(a+)+x*", "a?*", "^a$*"})
    void shouldTreatRegexMetacharactersLiterally(String query) {
        var literalPrefix = query.substring(0, query.indexOf('*'));

        var pattern = wildcardPrefixPattern(query);

        assertThat(pattern.matcher(literalPrefix + "suffix").find()).isTrue();
        assertThat(pattern.matcher("a").find()).isFalse();
    }

    @Test
    void shouldNotTreatDotAsAnyCharacterInWildcardQuery() {
        var pattern = wildcardPrefixPattern("user.name*");

        assertThat(pattern.matcher("user.name@mail.com").find()).isTrue();
        assertThat(pattern.matcher("userXname@mail.com").find()).isFalse();
    }

    @Test
    void shouldMatchExactValueIgnoringCase() {
        var pattern = caseInsensitiveExactPattern("test.user+tag*@test.com");

        assertThat(pattern.matcher("Test.User+Tag*@TEST.com").find()).isTrue();
        assertThat(pattern.matcher("testXuser+tag*@test.com").find()).isFalse();
        assertThat(pattern.matcher("other.test.user+tag*@test.com").find()).isFalse();
        assertThat(pattern.matcher("test.user+tag*@test.com.evil").find()).isFalse();
    }
}
