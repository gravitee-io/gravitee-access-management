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
package io.gravitee.am.reporter.file.vertx;

import io.gravitee.am.reporter.file.audit.ReportEntry;
import io.gravitee.am.reporter.file.formatter.Formatter;
import io.vertx.core.Vertx;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class VertxFileWriterTest {

    private static final ZoneOffset ZONE = ZoneOffset.UTC;
    private static final LocalDateTime NOW = LocalDateTime.now(ZONE);
    private static final String BASE_DIRECTORY = "am-log-test";
    private static final String FILENAME_PATTERN = "am-audit-logs" + "-" + VertxFileWriter.YYYY_MM_DD + '.' + "json";

    private VertxFileWriter<ReportEntry> vertxFileWriter;

    @Mock
    private File file;

    @Test
    public void shouldDeleteFile_should_return_true_if_retainDays_configuration_exceeds_file_lastModified_time() throws IOException {
        File tempDir = Files.createTempDirectory(BASE_DIRECTORY).toFile();
        tempDir.deleteOnExit();
        String filename = tempDir.getAbsolutePath() + File.separatorChar + FILENAME_PATTERN;


        vertxFileWriter = new VertxFileWriter<>(
                mock(Vertx.class),
                mock(Formatter.class),
                filename,
                10L);

        long currentTimeMs = NOW.toInstant(ZONE).toEpochMilli();
        when(file.lastModified()).thenReturn(NOW.toInstant(ZONE).minus(10, DAYS).minus(1, SECONDS).toEpochMilli());

        assertTrue(vertxFileWriter.shouldDeleteFile(file, currentTimeMs));

        // Cleanup
        tempDir.delete();
    }

    @Test
    public void shouldDeleteFile_should_return_false_if_retainDays_configuration_doesnt_exceed_file_lastModified_time() throws IOException {
        File tempDir = Files.createTempDirectory(BASE_DIRECTORY).toFile();
        tempDir.deleteOnExit();
        String filename = tempDir.getAbsolutePath() + File.separatorChar + FILENAME_PATTERN;

        vertxFileWriter = new VertxFileWriter<>(
                mock(Vertx.class),
                mock(Formatter.class),
                filename,
                10L);

        long currentTimeMs = NOW.toInstant(ZONE).toEpochMilli();
        when(file.lastModified()).thenReturn(NOW.toInstant(ZONE).minus(10, DAYS).plus(1, SECONDS).toEpochMilli());

        assertFalse(vertxFileWriter.shouldDeleteFile(file, currentTimeMs));

        // Cleanup
        tempDir.delete();
    }

    @Test
    public void shouldDeleteFile_should_return_false_if_retainDays_configuration_is_0() throws IOException {
        File tempDir = Files.createTempDirectory(BASE_DIRECTORY).toFile();
        tempDir.deleteOnExit();
        String filename = tempDir.getAbsolutePath() + File.separatorChar + FILENAME_PATTERN;

        vertxFileWriter = new VertxFileWriter<>(
                mock(Vertx.class),
                mock(Formatter.class),
                filename,
                0L);

        long currentTimeMs = NOW.toInstant(ZONE).toEpochMilli();

        assertFalse(vertxFileWriter.shouldDeleteFile(file, currentTimeMs));
        verify(file, never()).lastModified();

        // Cleanup
        tempDir.delete();
    }

    @Test
    public void shouldNotRemoveOldFiles_noRetainDays() throws IOException {
        File tempDir = Files.createTempDirectory(BASE_DIRECTORY).toFile();
        tempDir.deleteOnExit();
        String filename = tempDir.getAbsolutePath() + File.separatorChar + FILENAME_PATTERN;

        // Formatter for date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd");

        vertxFileWriter = new VertxFileWriter<>(
                mock(Vertx.class),
                mock(Formatter.class),
                filename,
                0L);

        // Create old log file (older than 1 day)
        String oldDate = LocalDate.now().minusDays(2).format(formatter);
        File oldLog = new File(tempDir, "am-audit-logs" + "-" + oldDate + '.' + "json");
        oldLog.createNewFile();
        oldLog.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2));

        // Run method
        vertxFileWriter.removeOldFiles();

        // Assert results
        assertTrue(oldLog.exists());

        // Cleanup
        oldLog.delete();
        tempDir.delete();
    }

    @Test
    public void shouldRemoveOldFiles() throws IOException {
        File tempDir = Files.createTempDirectory(BASE_DIRECTORY).toFile();
        tempDir.deleteOnExit();
        String filename = tempDir.getAbsolutePath() + File.separatorChar + FILENAME_PATTERN;

        // Formatter for date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd");

        vertxFileWriter = new VertxFileWriter<>(
                mock(Vertx.class),
                mock(Formatter.class),
                filename,
                1L);

        // Create old log file (older than 1 day)
        String oldDate = LocalDate.now().minusDays(2).format(formatter);
        File oldLog = new File(tempDir, "am-audit-logs" + "-" + oldDate + '.' + "json");
        oldLog.createNewFile();
        oldLog.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2));

        // Create recent log file (should not be deleted)
        String recentDate = LocalDate.now().format(formatter);
        File recentLog = new File(tempDir, "am-audit-logs" + "-" + recentDate + '.' + "json");
        recentLog.createNewFile();
        recentLog.setLastModified(System.currentTimeMillis());

        // Run method
        vertxFileWriter.removeOldFiles();

        // Assert results
        assertFalse(oldLog.exists());
        assertTrue(recentLog.exists());

        // Cleanup
        recentLog.delete();
        tempDir.delete();
    }

    @Test
    public void shouldCreateNewFileOnRollover() throws IOException {
        File tempDir = Files.createTempDirectory(BASE_DIRECTORY).toFile();
        tempDir.deleteOnExit();
        
        String templateFilename = tempDir.getAbsolutePath() + File.separatorChar + "test-audit-logs-yyyy_mm_dd.json";
        Vertx vertx = Vertx.vertx();
        
        try {
            vertxFileWriter = new VertxFileWriter<>(
                    vertx,
                    mock(Formatter.class),
                    templateFilename,
                    1L);

            AtomicReference<String> firstFilename = new AtomicReference<>();
            AtomicReference<String> secondFilename = new AtomicReference<>();
            AtomicReference<Boolean> testCompleted = new AtomicReference<>(false);

            vertxFileWriter.initialize().onComplete(initResult -> {
                if (initResult.succeeded()) {
                    firstFilename.set(getCurrentFilename(vertxFileWriter));
                    simulateRollover(vertxFileWriter);
                    secondFilename.set(getCurrentFilename(vertxFileWriter));
                    testCompleted.set(true);
                } else {
                    testCompleted.set(true);
                }
            });
            
            Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .until(testCompleted::get);
            
            String first = firstFilename.get();
            String second = secondFilename.get();
            
            // Validate that pattern replacement works and different files are created
            assertNotNull("First filename should not be null", first);
            assertNotNull("Second filename should not be null", second);
            assertFalse("First filename should not contain the pattern 'yyyy_mm_dd'", 
                       first.contains("yyyy_mm_dd"));
            assertFalse("Second filename should not contain the pattern 'yyyy_mm_dd'", 
                       second.contains("yyyy_mm_dd"));
            assertTrue("First filename should contain date format (YYYY_MM_DD)", 
                      first.matches(".*\\d{4}_\\d{2}_\\d{2}.*"));
            assertTrue("Second filename should contain date format (YYYY_MM_DD)", 
                      second.matches(".*\\d{4}_\\d{2}_\\d{2}.*"));
            assertFalse("Rollover should create different filenames - both files have same name: " + first, 
                       first.equals(second));
            
        } finally {
            vertx.close();
            tempDir.delete();
        }
    }
    
    private String getCurrentFilename(VertxFileWriter<ReportEntry> writer) {
        try {
            java.lang.reflect.Field filenameField = VertxFileWriter.class.getDeclaredField("filename");
            filenameField.setAccessible(true);
            return (String) filenameField.get(writer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get filename", e);
        }
    }
    
    private void simulateRollover(VertxFileWriter<ReportEntry> writer) {
        try {
            java.lang.reflect.Method setFileMethod = VertxFileWriter.class.getDeclaredMethod("setFile", ZonedDateTime.class);
            setFileMethod.setAccessible(true);
            ZonedDateTime nextDay = ZonedDateTime.now().plusDays(1);
            setFileMethod.invoke(writer, nextDay);
        } catch (Exception e) {
            throw new RuntimeException("Failed to simulate rollover", e);
        }
    }

}
