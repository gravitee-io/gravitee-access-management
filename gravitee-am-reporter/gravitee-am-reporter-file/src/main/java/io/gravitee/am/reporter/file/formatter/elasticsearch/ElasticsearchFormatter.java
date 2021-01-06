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
package io.gravitee.am.reporter.file.formatter.elasticsearch;

import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.file.audit.AuditEntry;
import io.gravitee.am.reporter.file.audit.ReportEntry;
import io.gravitee.am.reporter.file.formatter.AbstractFormatter;
import io.gravitee.am.reporter.file.formatter.elasticsearch.freemarker.FreeMarkerComponent;
import io.gravitee.node.api.Node;
import io.gravitee.reporter.api.Reportable;
import io.vertx.core.buffer.Buffer;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ElasticsearchFormatter<T extends ReportEntry> extends AbstractFormatter<T> {

    /** Index simple date format **/
    private final DateTimeFormatter dtf, sdf;

    private static String hostname;

    static {
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "unknown";
        }
    }

    @Autowired
    private FreeMarkerComponent freeMarkerComponent;

    public ElasticsearchFormatter() {
        this.dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS[XXX]").withZone(ZoneId.systemDefault());
        this.sdf = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault());
    }

    @Override
    public Buffer format0(T data) {
        if (data instanceof AuditEntry) {
            return getSource((AuditEntry) data);
        }

        return null;
    }

    /**
     * Convert a {@link AuditEntry} into an ES bulk line.
     *
     * @param audit A request audit
     * @return ES bulk line
     */
    private Buffer getSource(final AuditEntry audit) {
        final Map<String, Object> data = new HashMap<>(5);

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

    static final class Fields {
        static final String SPECIAL_TIMESTAMP = "@timestamp";
    }
}
