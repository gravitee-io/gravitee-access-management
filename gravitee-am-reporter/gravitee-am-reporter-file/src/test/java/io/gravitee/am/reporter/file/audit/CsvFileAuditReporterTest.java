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

import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.file.JUnitConfiguration;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = JUnitConfiguration.class, loader = AnnotationConfigContextLoader.class)
public class CsvFileAuditReporterTest extends FileAuditReporterTest {
    private static final char LF = '\n';
    private static final char CR = '\r';
    private final static byte[] END_OF_LINE = new byte[]{CR, LF};

    static {
        System.setProperty(FileAuditReporter.REPORTERS_FILE_ENABLED, "true");
        System.setProperty(FileAuditReporter.REPORTERS_FILE_OUTPUT, "CSV");
        System.setProperty(FileAuditReporter.REPORTERS_FILE_DIRECTORY, "target");
    }

    @Override
    protected void checkAuditLogs(List<Audit> reportables, int loop) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(buildAuditLogsFilename()));
        assertEquals(10, lines.size());
    }
}