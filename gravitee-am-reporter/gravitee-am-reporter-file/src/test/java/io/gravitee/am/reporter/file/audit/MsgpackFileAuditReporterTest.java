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
package io.gravitee.am.reporter.file.audit;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.file.JUnitConfiguration;
import io.gravitee.am.reporter.file.formatter.Type;
import org.junit.runner.RunWith;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = JUnitConfiguration.class, loader = AnnotationConfigContextLoader.class)
public class MsgpackFileAuditReporterTest extends FileAuditReporterTest {
    private static final char LF = '\n';
    private static final char CR = '\r';
    private final static byte[] END_OF_LINE = new byte[]{CR, LF};

    static {
        System.setProperty(FileAuditReporter.REPORTERS_FILE_ENABLED, "true");
        System.setProperty(FileAuditReporter.REPORTERS_FILE_OUTPUT, "MESSAGE_PACK");
        System.setProperty(FileAuditReporter.REPORTERS_FILE_DIRECTORY, "target");
    }

    @Override
    protected void checkAuditLogs(List<Audit> reportables, int loop) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(buildAuditLogsFilename()));
        List<byte[]> lines = new ArrayList<>();
        List<Byte> buffer = new ArrayList<>();
        for (int i = 0; i < bytes.length; ++i) {
            if (bytes[i] == CR && (i+1 <bytes.length) && bytes[i+1] == LF) {
                i++; // skip next byte
                byte[] line = new byte[buffer.size()];
                for (int j =0; j < line.length; j++) {
                    line[j] = buffer.get(j).byteValue();
                }
                lines.add(line);
                buffer = new ArrayList<>();
            } else {
                buffer.add(bytes[i]);
            }
        }

        final ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        for (int i = 0; i < loop; ++i) {
            AuditEntry readAudit = mapper.readValue(lines.get(i), AuditEntry.class);
            assertReportEqualsTo(reportables.get(i), readAudit);
        }
    }
}