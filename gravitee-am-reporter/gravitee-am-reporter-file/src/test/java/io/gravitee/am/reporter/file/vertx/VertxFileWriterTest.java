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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.ZoneId;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(VertxFileWriterTest.class);

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
    public void shouldActuallyExecuteScheduledTimerWithMockedTime() throws IOException {
        File tempDir = Files.createTempDirectory(BASE_DIRECTORY).toFile();
        tempDir.deleteOnExit();
        String filename = tempDir.getAbsolutePath() + File.separatorChar + "test-audit-logs-yyyy_mm_dd.json";
        Vertx vertx = Vertx.vertx();
        
        try {
            // Mock time to just before midnight (11:59:59 PM) for 1-second timer delay
            ZonedDateTime justBeforeMidnight = ZonedDateTime.of(2025, 8, 1, 23, 59, 59, 0, ZoneId.systemDefault());
            AtomicReference<ZonedDateTime> mockTime = new AtomicReference<>(justBeforeMidnight);
            
            TimeProvider timeProvider = new TimeProvider() {
                @Override
                public ZonedDateTime now() {
                    return mockTime.get();
                }
                
                @Override
                public ZonedDateTime now(TimeZone timeZone) {
                    return mockTime.get();
                }
            };
            
            // Create a writer that doesn't override setFile during initialization
            VertxFileWriter<ReportEntry> writer = new VertxFileWriter<ReportEntry>(
                    vertx, mock(Formatter.class), filename, 1L, timeProvider);
            
            // Initialize the writer - this schedules rollover for next midnight (1 second later)
            writer.initialize();
            
            // Wait for the scheduled timer to create the 08_02 file
            String expectedRolloverFileName = "test-audit-logs-2025_08_02.json";
            File expectedRolloverFile = new File(tempDir, expectedRolloverFileName);
            
            Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(expectedRolloverFile::exists);
           
            // Verify we have exactly the two expected files
            List<String> allFileNames = Arrays.stream(tempDir.listFiles())
                .map(File::getName)
                .toList();
            
            assertEquals("Should contain exactly 2 files", 2, allFileNames.size());
            assertTrue("Should contain 08_01 file", allFileNames.contains("test-audit-logs-2025_08_01.json"));
            assertTrue("Should contain 08_02 file", allFileNames.contains("test-audit-logs-2025_08_02.json"));
            
            LOGGER.info("🎉 Test completed successfully - scheduled timer created the expected rollover file!");
        } finally {
            vertx.close();
            tempDir.delete();
        }
    }

    @Test
    public void shouldHandleFileDeletionErrors() throws IOException {
        File tempDir = Files.createTempDirectory(BASE_DIRECTORY).toFile();
        tempDir.deleteOnExit();
        String filename = tempDir.getAbsolutePath() + File.separatorChar + "test-audit-logs-yyyy_mm_dd.json";
        Vertx vertx = Vertx.vertx();
        
        try {
            VertxFileWriter<ReportEntry> writer = new VertxFileWriter<>(
                    vertx, mock(Formatter.class), filename, 1L);
            
            // Initialize
            writer.initialize();
            
            // Create a file that can't be deleted (by making it read-only)
            File readOnlyFile = new File(tempDir, "readonly-file.json");
            try {
                readOnlyFile.createNewFile();
            } catch (IOException e) {
                fail("Failed to create read-only test file: " + e.getMessage());
            }
            readOnlyFile.setReadOnly();
            
            // This should not throw an exception due to error handling in deleteFile method
            try {
                writer.removeOldFiles();
            } catch (Exception e) {
                fail("removeOldFiles should not throw exception: " + e.getMessage());
            }
            
            // Clean up
            readOnlyFile.setWritable(true);
            
        } finally {
            vertx.close();
            tempDir.delete();
        }
    }

    @Test
    public void shouldHandleNullFilename() throws IOException {
        // Test null filename validation - hits validateAndNormalizeFilename()
        try {
            new VertxFileWriter<>(mock(Vertx.class), mock(Formatter.class), null, 1L);
            fail("Should throw IllegalArgumentException for null filename");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void shouldHandleEmptyFilename() throws IOException {
        // Test empty filename validation
        try {
            new VertxFileWriter<>(mock(Vertx.class), mock(Formatter.class), "", 1L);
            fail("Should throw IllegalArgumentException for empty filename");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void shouldHandleWhitespaceFilename() throws IOException {
        // Test whitespace-only filename validation
        try {
            new VertxFileWriter<>(mock(Vertx.class), mock(Formatter.class), "   ", 1L);
            fail("Should throw IllegalArgumentException for whitespace-only filename");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

}
