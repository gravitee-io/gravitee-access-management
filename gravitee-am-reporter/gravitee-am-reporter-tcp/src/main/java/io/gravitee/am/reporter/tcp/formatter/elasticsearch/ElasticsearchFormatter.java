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
package io.gravitee.am.reporter.tcp.formatter.elasticsearch;

import io.gravitee.am.reporter.tcp.audit.AuditEntry;
import io.gravitee.am.reporter.tcp.formatter.AbstractFormatter;
import io.gravitee.am.reporter.tcp.formatter.ReportEntry;
import io.gravitee.am.reporter.tcp.formatter.elasticsearch.freemarker.FreeMarkerComponent;
import io.vertx.core.buffer.Buffer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Formats an {@link AuditEntry} as an Elasticsearch bulk API line using a FreeMarker template.
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ElasticsearchFormatter<T extends ReportEntry> extends AbstractFormatter<T> {

    private final DateTimeFormatter dtf;
    private final DateTimeFormatter sdf;

    @Autowired
    private FreeMarkerComponent freeMarkerComponent;

    public ElasticsearchFormatter() {
        this.dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS[XXX]").withZone(ZoneId.systemDefault());
        this.sdf = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault());
    }

    @Override
    public Buffer format0(T data) {
        if (data instanceof AuditEntry auditEntry) {
            return getSource(auditEntry);
        }
        return null;
    }

    private Buffer getSource(final AuditEntry audit) {
        final Map<String, Object> data = new HashMap<>(3);
        data.put("date", sdf.format(audit.getTimestamp()));
        data.put(Fields.SPECIAL_TIMESTAMP, dtf.format(audit.getTimestamp()));
        data.put("audit", audit);
        return generateData("audit.ftl", data);
    }

    private Buffer generateData(String templateName, Map<String, Object> data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            freeMarkerComponent.generateFromTemplate(
                    "index/" + templateName,
                    data,
                    new OutputStreamWriter(baos));
            return Buffer.buffer(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    static final class Fields {
        static final String SPECIAL_TIMESTAMP = "@timestamp";
    }
}
