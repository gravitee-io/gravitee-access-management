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
package io.gravitee.am.service.reporter.attribute;

import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

/**
 * Whether a resolved value is a single value a reporter can export.
 *
 * @author GraviteeSource Team
 */
public final class ExportableValue {

    private ExportableValue() {
    }

    public static boolean isExportable(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().allMatch(ExportableValue::isScalar);
        }
        return isScalar(value);
    }

    private static boolean isScalar(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof UUID
                || value instanceof Date
                || value instanceof Temporal;
    }
}
