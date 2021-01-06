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
package io.gravitee.am.reporter.file.formatter.csv;

import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.file.audit.AuditEntry;
import io.gravitee.am.reporter.file.audit.ReportEntry;
import io.gravitee.am.reporter.file.formatter.Formatter;
import io.gravitee.reporter.api.Reportable;
import io.vertx.core.buffer.Buffer;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CsvFormatter<T extends ReportEntry> implements Formatter<T> {

    private final static AuditFormatter AUDIT_FORMATTER = new AuditFormatter();

    @Override
    public Buffer format(T data) {
        if (data instanceof AuditEntry) {
            return AUDIT_FORMATTER.format((AuditEntry) data);
        }
        return null;
    }
}
