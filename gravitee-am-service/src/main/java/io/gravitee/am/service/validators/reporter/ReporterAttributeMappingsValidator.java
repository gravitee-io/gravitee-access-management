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
import io.gravitee.am.service.validators.Validator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates the attribute mappings declared on a reporter. A mapping names an expression to read from
 * the audit context and the field name its value is exported under.
 *
 * @author GraviteeSource Team
 */
@Component
public class ReporterAttributeMappingsValidator implements Validator<List<ReporterAttributeMapping>, ReporterAttributeMappingsValidator.ValidationResult> {

    static final int MAX_COUNT = 20;
    static final int MAX_EXPRESSION_LENGTH = 512;
    static final int MAX_EXPORTED_NAME_LENGTH = 64;

    /** Keeps a rejection message readable when the offending value is itself over-long. */
    private static final int MAX_DESCRIBED_LENGTH = 80;

    private static final Pattern EXPORTED_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    public record ValidationResult(List<String> invalidExpressions,
                                   List<String> invalidExportedNames,
                                   List<String> duplicateExportedNames,
                                   Integer exceededMaxCount) {

        public boolean isInvalid() {
            return !invalidExpressions.isEmpty()
                    || !invalidExportedNames.isEmpty()
                    || !duplicateExportedNames.isEmpty()
                    || exceededMaxCount != null;
        }

        public static ValidationResult valid() {
            return new ValidationResult(List.of(), List.of(), List.of(), null);
        }

        public String describe() {
            if (exceededMaxCount != null) {
                return "Maximum number of reporter attribute mappings exceeded (max: " + exceededMaxCount + ")";
            }
            if (!invalidExpressions.isEmpty()) {
                return "Invalid reporter attribute mapping expressions: " + invalidExpressions;
            }
            if (!invalidExportedNames.isEmpty()) {
                return "Invalid reporter attribute mapping exported names: " + invalidExportedNames;
            }
            return "Duplicate reporter attribute mapping exported names: " + duplicateExportedNames;
        }
    }

    @Override
    public ValidationResult validate(List<ReporterAttributeMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return ValidationResult.valid();
        }
        if (mappings.size() > MAX_COUNT) {
            return new ValidationResult(List.of(), List.of(), List.of(), MAX_COUNT);
        }

        List<String> invalidExpressions = new ArrayList<>();
        List<String> invalidExportedNames = new ArrayList<>();
        List<String> exportedNames = new ArrayList<>();

        for (ReporterAttributeMapping mapping : mappings) {
            // a null entry carries neither half of the mapping, so it is reported against both.
            // Values are checked exactly as supplied rather than trimmed: what is validated here is
            // what gets persisted and later evaluated, so a surrounding space has to be rejected
            // instead of quietly accepted and stored.
            String expression = mapping == null ? null : mapping.expression();
            String exportedName = mapping == null ? null : mapping.exportedName();

            if (!isValidExpression(expression)) {
                invalidExpressions.add(describeValue(expression));
            }
            if (isValidExportedName(exportedName)) {
                exportedNames.add(exportedName);
            } else {
                invalidExportedNames.add(describeValue(exportedName));
            }
        }

        List<String> duplicateExportedNames = exportedNames.stream()
                .filter(name -> Collections.frequency(exportedNames, name) > 1)
                .distinct()
                .toList();

        return new ValidationResult(
                invalidExpressions.stream().distinct().toList(),
                invalidExportedNames.stream().distinct().toList(),
                duplicateExportedNames,
                null);
    }

    private static boolean isValidExpression(String expression) {
        return expression != null
                && !expression.isBlank()
                && expression.length() <= MAX_EXPRESSION_LENGTH;
    }

    private static boolean isValidExportedName(String exportedName) {
        return exportedName != null
                && exportedName.length() <= MAX_EXPORTED_NAME_LENGTH
                && EXPORTED_NAME_PATTERN.matcher(exportedName).matches();
    }

    private static String describeValue(String value) {
        if (value == null || value.isEmpty()) {
            return "<empty>";
        }
        return value.length() <= MAX_DESCRIBED_LENGTH ? value : value.substring(0, MAX_DESCRIBED_LENGTH) + "...";
    }
}
