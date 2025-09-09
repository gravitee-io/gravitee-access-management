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
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

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
    public void shouldActuallyExecuteScheduledTimerWithMockedTime() throws IOException {
        File tempDir = Files.createTempDirectory(BASE_DIRECTORY).toFile();
        tempDir.deleteOnExit();
        String filename = tempDir.getAbsolutePath() + File.separatorChar + "test-audit-logs-yyyy_mm_dd.json";
        Vertx vertx = Vertx.vertx();
    
        // Mock time progression: first call returns just before midnight, second call returns midnight
        ZonedDateTime justBeforeMidnight = ZonedDateTime.of(2025, 8, 1, 23, 59, 59, 0, TimeZone.getDefault().toZoneId());
        ZonedDateTime midnight = ZonedDateTime.of(2025, 8, 2, 0, 0, 0, 0, TimeZone.getDefault().toZoneId());
        
        AtomicInteger nowCallCount = new AtomicInteger(0);
        AtomicInteger nowWithTzCallCount = new AtomicInteger(0);
        
        VertxFileWriter.TimeProvider timeProvider = new VertxFileWriter.TimeProvider() {
            @Override
            public ZonedDateTime now() {
                int count = nowCallCount.getAndIncrement();
                if (count == 0) {
                    return justBeforeMidnight;
                } else {
                    return midnight;
                }
            }
            
            @Override
            public ZonedDateTime now(TimeZone timeZone) {
                int count = nowWithTzCallCount.getAndIncrement();
                if (count == 0) {
                    ZonedDateTime adjustedTime = justBeforeMidnight.withZoneSameInstant(timeZone.toZoneId());
                    return adjustedTime;
                } else {
                    ZonedDateTime adjustedTime = midnight.withZoneSameInstant(timeZone.toZoneId());
                    return adjustedTime;
                }
            }
        };
        
        // Create a writer with the mock time provider
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

        // Clean up
        expectedRolloverFile.delete();
        vertx.close();
        tempDir.delete();

    }

    @Test
    public void shouldHandleFileDeletionErrors() throws IOException {
        File tempDir = Files.createTempDirectory(BASE_DIRECTORY).toFile();
        tempDir.deleteOnExit();
        String filename = tempDir.getAbsolutePath() + File.separatorChar + "test-audit-logs-yyyy_mm_dd.json";
        Vertx vertx = Vertx.vertx();

        VertxFileWriter<ReportEntry> writer = new VertxFileWriter<>(
                vertx, mock(Formatter.class), filename, 1L);
        
        // Initialize
        writer.initialize();
        
        // Create a file that can't be deleted (by making it read-only)
        File readOnlyFile = new File(tempDir, "readonly-file.json");
        readOnlyFile.createNewFile();
        readOnlyFile.setReadOnly();
        
        // This should not throw an exception due to error handling in deleteFile method
        try {
            writer.removeOldFiles();
        } catch (Exception e) {
            fail("removeOldFiles should not throw exception: " + e.getMessage());
        }
        
        // Clean up
        readOnlyFile.setWritable(true);
        vertx.close();
        tempDir.delete();
    }

    @Test
    public void shouldHandleNullFilename() {
        // Test null filename validation - hits validateAndNormalizeFilename()
        assertThrows("Should throw IllegalArgumentException for null filename", 
                    IllegalArgumentException.class, 
                    () -> new VertxFileWriter<>(mock(Vertx.class), mock(Formatter.class), null, 1L));
    }

    @Test
    public void shouldHandleEmptyFilename() {
        // Test empty filename validation
        assertThrows("Should throw IllegalArgumentException for empty filename", 
                    IllegalArgumentException.class, 
                    () -> new VertxFileWriter<>(mock(Vertx.class), mock(Formatter.class), "", 1L));
    }

    @Test
    public void shouldHandleWhitespaceFilename() {
        // Test whitespace-only filename validation
        assertThrows("Should throw IllegalArgumentException for whitespace-only filename", 
                    IllegalArgumentException.class, 
                    () -> new VertxFileWriter<>(mock(Vertx.class), mock(Formatter.class), "   ", 1L));
    }

}
