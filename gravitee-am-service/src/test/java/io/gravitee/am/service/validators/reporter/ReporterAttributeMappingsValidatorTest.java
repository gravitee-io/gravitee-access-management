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
package io.gravitee.am.service.validators.reporter;

import io.gravitee.am.model.ReporterAttributeMapping;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static io.gravitee.am.service.validators.reporter.ReporterAttributeMappingsValidator.MAX_COUNT;

/**
 * @author GraviteeSource Team
 */
class ReporterAttributeMappingsValidatorTest {

    private static final String VALID_EXPRESSION = "{#context.attributes['user'].additionalInformation['sub']}";

    private final ReporterAttributeMappingsValidator validator = new ReporterAttributeMappingsValidator();

    private static ReporterAttributeMapping mapping(String expression, String exportedName) {
        return new ReporterAttributeMapping(expression, exportedName);
    }

    private static String repeat(char c, int length) {
        return String.valueOf(c).repeat(length);
    }

    /** An expression of exactly {@code length} characters that is otherwise valid. */
    private static String expressionOfLength(int length) {
        return "{" + repeat('a', length - 2) + "}";
    }

    @Nested
    class NothingDeclared {

        @Test
        void nullListIsValid() {
            assertThat(validator.validate(null).isInvalid()).isFalse();
        }

        @Test
        void emptyListIsValid() {
            assertThat(validator.validate(List.of()).isInvalid()).isFalse();
        }
    }

    @Nested
    class Count {

        private List<ReporterAttributeMapping> mappings(int count) {
            return IntStream.range(0, count)
                    .mapToObj(i -> mapping(VALID_EXPRESSION, "field_" + i))
                    .toList();
        }

        @Test
        void atTheMaximumIsValid() {
            assertThat(validator.validate(mappings(MAX_COUNT)).isInvalid()).isFalse();
        }

        @Test
        void oneOverTheMaximumIsRejected() {
            var result = validator.validate(mappings(MAX_COUNT + 1));

            assertThat(result.isInvalid()).isTrue();
            assertThat(result.exceededMaxCount()).isEqualTo(MAX_COUNT);
            assertThat(result.describe()).isEqualTo("Maximum number of reporter attribute mappings exceeded (max: 20)");
        }

        @Test
        void countIsCheckedBeforeTheContents() {
            // an over-long list of otherwise invalid mappings reports the count, not every entry
            var tooMany = IntStream.range(0, MAX_COUNT + 1)
                    .mapToObj(i -> mapping("no braces", "not a name"))
                    .toList();

            var result = validator.validate(tooMany);

            assertThat(result.exceededMaxCount()).isEqualTo(MAX_COUNT);
            assertThat(result.invalidExpressions()).isEmpty();
            assertThat(result.invalidExportedNames()).isEmpty();
        }
    }

    @Nested
    class Expressions {

        @Test
        void aBracedExpressionIsValid() {
            assertThat(validator.validate(List.of(mapping(VALID_EXPRESSION, "user_sub"))).isInvalid()).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {
                "   ",                                  // whitespace only
                "#context.attributes['user']",          // no braces at all
                "{#context.attributes['user']",         // opening brace only
                "#context.attributes['user']}",         // closing brace only
                "{}",                                   // braces with nothing to evaluate
                "{",                                    // a single brace satisfies neither end
                " {#context.attributes['user']}",       // leading space: stored as-is, so rejected
                "{#context.attributes['user']} "        // trailing space
        })
        void rejectsAnExpressionThatIsNotWrappedInBraces(String expression) {
            var result = validator.validate(List.of(mapping(expression, "user_sub")));

            assertThat(result.isInvalid()).isTrue();
            assertThat(result.invalidExpressions()).hasSize(1);
            assertThat(result.describe()).startsWith("Invalid reporter attribute mapping expressions:");
        }

        @Test
        void acceptsAnExpressionAtTheLengthLimit() {
            var atLimit = expressionOfLength(ReporterAttributeMappingsValidator.MAX_EXPRESSION_LENGTH);

            assertThat(atLimit).hasSize(ReporterAttributeMappingsValidator.MAX_EXPRESSION_LENGTH);
            assertThat(validator.validate(List.of(mapping(atLimit, "user_sub"))).isInvalid()).isFalse();
        }

        @Test
        void rejectsAnExpressionOneOverTheLengthLimit() {
            var overLimit = expressionOfLength(ReporterAttributeMappingsValidator.MAX_EXPRESSION_LENGTH + 1);

            var result = validator.validate(List.of(mapping(overLimit, "user_sub")));

            assertThat(result.isInvalid()).isTrue();
            // abbreviated so a rejected 513-character expression does not become a 513-character message
            assertThat(result.invalidExpressions()).containsExactly(overLimit.substring(0, 80) + "...");
        }

        @Test
        void reportsEveryDistinctInvalidExpressionOnce() {
            var result = validator.validate(List.of(
                    mapping("no braces", "first"),
                    mapping("no braces", "second"),
                    mapping("{unterminated", "third")));

            assertThat(result.invalidExpressions()).containsExactly("no braces", "{unterminated");
        }
    }

    @Nested
    class ExportedNames {

        @ParameterizedTest
        @ValueSource(strings = {"user_sub", "USER_SUB", "sub", "field0", "_leading", "trailing_", "0"})
        void acceptsWordCharacters(String exportedName) {
            assertThat(validator.validate(List.of(mapping(VALID_EXPRESSION, exportedName))).isInvalid()).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {
                "   ",              // whitespace only
                "user.sub",         // dot
                "user-sub",         // hyphen
                "user sub",         // space
                " user_sub",        // leading space
                "user_sub ",        // trailing space
                "user$sub",         // symbol
                "user/sub",         // separator
                "utilisateur_é"     // non-ascii
        })
        void rejectsAnythingElse(String exportedName) {
            var result = validator.validate(List.of(mapping(VALID_EXPRESSION, exportedName)));

            assertThat(result.isInvalid()).isTrue();
            assertThat(result.invalidExportedNames()).hasSize(1);
            assertThat(result.describe()).startsWith("Invalid reporter attribute mapping exported names:");
        }

        @Test
        void acceptsANameAtTheLengthLimit() {
            var atLimit = repeat('a', ReporterAttributeMappingsValidator.MAX_EXPORTED_NAME_LENGTH);

            assertThat(validator.validate(List.of(mapping(VALID_EXPRESSION, atLimit))).isInvalid()).isFalse();
        }

        @Test
        void rejectsANameOneOverTheLengthLimit() {
            var overLimit = repeat('a', ReporterAttributeMappingsValidator.MAX_EXPORTED_NAME_LENGTH + 1);

            var result = validator.validate(List.of(mapping(VALID_EXPRESSION, overLimit)));

            assertThat(result.isInvalid()).isTrue();
            // 65 characters is under the abbreviation threshold, so it is named in full
            assertThat(result.invalidExportedNames()).containsExactly(overLimit);
        }

        @Test
        void describesAnEmptyNameReadably() {
            var result = validator.validate(List.of(mapping(VALID_EXPRESSION, null)));

            assertThat(result.invalidExportedNames()).containsExactly("<empty>");
        }
    }

    @Nested
    class Duplicates {

        @Test
        void rejectsTwoMappingsExportingTheSameName() {
            var result = validator.validate(List.of(
                    mapping(VALID_EXPRESSION, "user_sub"),
                    mapping("{#context.attributes['user'].id}", "user_sub")));

            assertThat(result.isInvalid()).isTrue();
            assertThat(result.duplicateExportedNames()).containsExactly("user_sub");
            assertThat(result.describe()).isEqualTo("Duplicate reporter attribute mapping exported names: [user_sub]");
        }

        @Test
        void reportsEachDuplicatedNameOnceRegardlessOfHowOftenItRepeats() {
            var result = validator.validate(List.of(
                    mapping(VALID_EXPRESSION, "a"),
                    mapping(VALID_EXPRESSION, "a"),
                    mapping(VALID_EXPRESSION, "a"),
                    mapping(VALID_EXPRESSION, "b"),
                    mapping(VALID_EXPRESSION, "b")));

            assertThat(result.duplicateExportedNames()).containsExactly("a", "b");
        }

        @Test
        void namesDifferingOnlyByCaseAreNotDuplicates() {
            // exported names reach backends that treat case as significant, so they are distinct fields
            assertThat(validator.validate(List.of(
                    mapping(VALID_EXPRESSION, "user_sub"),
                    mapping(VALID_EXPRESSION, "USER_SUB"))).isInvalid()).isFalse();
        }

        @Test
        void repeatingTheSameExpressionUnderDifferentNamesIsAllowed() {
            assertThat(validator.validate(List.of(
                    mapping(VALID_EXPRESSION, "user_sub"),
                    mapping(VALID_EXPRESSION, "subject"))).isInvalid()).isFalse();
        }
    }

    @Nested
    class Reporting {

        @Test
        void aNullEntryIsReportedAgainstBothHalves() {
            var mappings = new ArrayList<ReporterAttributeMapping>();
            mappings.add(null);

            var result = validator.validate(mappings);

            assertThat(result.isInvalid()).isTrue();
            assertThat(result.invalidExpressions()).containsExactly("<empty>");
            assertThat(result.invalidExportedNames()).containsExactly("<empty>");
        }

        @Test
        void expressionsAreDescribedBeforeNamesWhenBothAreInvalid() {
            var result = validator.validate(List.of(mapping("no braces", "not a name")));

            assertThat(result.invalidExpressions()).isNotEmpty();
            assertThat(result.invalidExportedNames()).isNotEmpty();
            assertThat(result.describe()).startsWith("Invalid reporter attribute mapping expressions:");
        }

        @Test
        void anInvalidNameIsNotAlsoCountedAsADuplicate() {
            var result = validator.validate(List.of(
                    mapping(VALID_EXPRESSION, "bad name"),
                    mapping(VALID_EXPRESSION, "bad name")));

            assertThat(result.invalidExportedNames()).containsExactly("bad name");
            assertThat(result.duplicateExportedNames()).isEmpty();
        }

        @Test
        void aValidResultDescribesNothingAndIsUnmodifiable() {
            var valid = ReporterAttributeMappingsValidator.ValidationResult.valid();

            assertThat(valid.isInvalid()).isFalse();
            assertThat(valid.invalidExpressions()).isEmpty();
            assertThat(valid.invalidExportedNames()).isEmpty();
            assertThat(valid.duplicateExportedNames()).isEmpty();
            assertThat(valid.exceededMaxCount()).isNull();
        }

        @Test
        void mappingsAreAcceptedInAnyOrder() {
            var mappings = new ArrayList<>(List.of(
                    mapping(VALID_EXPRESSION, "a"),
                    mapping("{#context.attributes['client']}", "b")));
            Collections.reverse(mappings);

            assertThat(validator.validate(mappings).isInvalid()).isFalse();
        }
    }
}
