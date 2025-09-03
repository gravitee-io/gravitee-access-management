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
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * Time provider interface to allow time mocking in tests
 */
interface TimeProvider {
    ZonedDateTime now();
    ZonedDateTime now(TimeZone timeZone);
}

/**
 * Default time provider implementation
 */
class DefaultTimeProvider implements TimeProvider {
    @Override
    public ZonedDateTime now() {
        return ZonedDateTime.now();
    }
    
    @Override
    public ZonedDateTime now(TimeZone timeZone) {
        return ZonedDateTime.now(timeZone.toZoneId());
    }
}

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VertxFileWriter<T extends ReportEntry> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VertxFileWriter.class);

    /**
     * {@code \u000a} linefeed LF ('\n').
     *
     * @see <a href="http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.10.6">JLF: Escape Sequences
     *      for Character and String Literals</a>
     * @since 2.2
     */
    private static final char LF = '\n';

    /**
     * {@code \u000d} carriage return CR ('\r').
     *
     * @see <a href="http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.10.6">JLF: Escape Sequences
     *      for Character and String Literals</a>
     * @since 2.2
     */
    private static final char CR = '\r';

    private static final byte[] END_OF_LINE = new byte[]{CR, LF};

    private final Vertx vertx;

    private String filename;
    private String templateFilename;

    private final Formatter<T> formatter;

    private AsyncFile asyncFile;

    private static final String ROLLOVER_FILE_DATE_FORMAT = "yyyy_MM_dd";
    public static final String YYYY_MM_DD = "yyyy_mm_dd";
    
    // File retention constants
    private static final long MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000L;

    private Long rolloverTimerId;

    private final SimpleDateFormat fileDateFormat = new SimpleDateFormat(ROLLOVER_FILE_DATE_FORMAT);

    private long retainDays = -1;

    private Pattern rolloverFiles;
    
    private final TimeProvider timeProvider;

    public VertxFileWriter(Vertx vertx, Formatter<T> formatter, String filename) throws IOException {
        this(vertx, formatter, filename, new DefaultTimeProvider());
    }
    
    public VertxFileWriter(Vertx vertx, Formatter<T> formatter, String filename, TimeProvider timeProvider) throws IOException {
        this.vertx = vertx;
        this.formatter = formatter;
        this.timeProvider = timeProvider;
        this.filename = validateAndNormalizeFilename(filename);
        
        initializeFileComponents();
    }

    public VertxFileWriter(Vertx vertx, Formatter<T> formatter, String filename, long retainDays) throws IOException {
        this(vertx, formatter, filename);
        this.retainDays = retainDays;
    }
    
    public VertxFileWriter(Vertx vertx, Formatter<T> formatter, String filename, long retainDays, TimeProvider timeProvider) throws IOException {
        this(vertx, formatter, filename, timeProvider);
        this.retainDays = retainDays;
    }
    
    private String validateAndNormalizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid filename");
        }
        return filename.trim();
    }
    
    private void initializeFileComponents() {
        File file = new File(this.filename);
        this.templateFilename = file.getName();
        this.rolloverFiles = Pattern.compile(templateFilename.replaceFirst(YYYY_MM_DD, "([0-9]{4}_[0-9]{2}_[0-9]{2})"));
        this.fileDateFormat.setTimeZone(TimeZone.getDefault());
    }

    public Future<Void> initialize() {
        LOGGER.debug("Initializing file reporter, retainDays: {}", retainDays);
        // Calculate Today's Midnight, based on Configured TimeZone (will be in past, even if by a few milliseconds)
        ZonedDateTime now = timeProvider.now(TimeZone.getDefault());

        // This will schedule the rollover event to the next midnight
        scheduleNextRollover(now);

        // Clean up any old files since we started last
        this.removeOldFiles();

        return setFile(now);
    }

    protected Future<Void> setFile(ZonedDateTime now) {
        Promise<Void> promise = Promise.promise();

        synchronized (this) {
            try {
                String newFilename = prepareNewFilename(now);
                if (newFilename == null) {
                    promise.fail(new IOException("Cannot prepare filename for rollover"));
                    return promise.future();
                }

                openNewFile(newFilename, promise);
            } catch (IOException ioe) {
                promise.fail(ioe);
            }
        }

        return promise.future();
    }
    
    private String prepareNewFilename(ZonedDateTime now) throws IOException {
        File file = new File(filename);
        filename = file.getCanonicalPath();
        
        File dir = new File(file.getParent());
        if (!isDirectoryWritable(dir)) {
            LOGGER.error("Cannot write reporter data to directory {}", dir);
            return null;
        }

        String newDate = fileDateFormat.format(new Date(now.toInstant().toEpochMilli()));
        return dir.getAbsolutePath() + File.separatorChar + 
               templateFilename.replaceFirst("(?i)" + YYYY_MM_DD, newDate);
    }
    
    private boolean isDirectoryWritable(File dir) {
        return dir.isDirectory() && dir.canWrite();
    }
    
    private void openNewFile(String newFilename, Promise<Void> promise) {
        filename = newFilename;
        LOGGER.info("Initializing file reporter to write into file: {}", filename);

        AsyncFile oldAsyncFile = asyncFile;

        vertx.fileSystem().open(filename, new OpenOptions()
                .setAppend(true)
                .setCreate(true)
                .setDsync(true), event -> {
            if (event.succeeded()) {
                asyncFile = event.result();
                closeOldFileIfExists(oldAsyncFile);
                promise.complete();
            } else {
                LOGGER.error("An error occurs while starting file writer [{}]", filename, event.cause());
                promise.fail(event.cause());
            }
        });
    }
    
    private void closeOldFileIfExists(AsyncFile oldAsyncFile) {
        if (oldAsyncFile != null) {
            close(oldAsyncFile).onFailure(throwable -> {
                LOGGER.error("An error occurs while closing file writer [{}]", filename, throwable);
            });
        }
    }

    public void write(T data) {
        if (asyncFile != null) {
            Buffer payload = formatter.format(data);
            if (payload != null) {
                asyncFile.write(payload.appendBytes(END_OF_LINE));
            }
        }
    }

    private Future<Void> close(AsyncFile asyncFile) {
        Promise<Void> promise = Promise.promise();

        if (asyncFile != null) {
            asyncFile.close(event -> {
                if (event.succeeded()) {
                    LOGGER.info("File writer is now closed [{}]", this.filename);
                    promise.complete();
                } else {
                    LOGGER.error("An error occurs while closing file writer [{}]", this.filename, event.cause());
                    promise.fail(event.cause());
                }
            });
        } else {
            promise.complete();
        }

        return promise.future();
    }

    private void scheduleNextRollover(ZonedDateTime now) {
        cancelExistingTimer();
        
        ZonedDateTime midnight = toMidnight(now);
        LOGGER.debug("Scheduling next rollover for {} (current time: {})", midnight, now);

        long delay = calculateDelayToMidnight(now, midnight);
        scheduleRolloverTimer(midnight, delay);
    }
    
    private void cancelExistingTimer() {
        if (rolloverTimerId != null) {
            vertx.cancelTimer(rolloverTimerId);
        }
    }
    
    private long calculateDelayToMidnight(ZonedDateTime now, ZonedDateTime midnight) {
        return midnight.toInstant().toEpochMilli() - now.toInstant().toEpochMilli();
    }
    
    private void scheduleRolloverTimer(ZonedDateTime midnight, long delay) {
        rolloverTimerId = vertx.setTimer(delay, timerId -> executeRollover(midnight));
        
        LOGGER.debug("Rollover timer scheduled with ID: {} for delay: {}ms (scheduled for: {})", 
                    rolloverTimerId, delay, midnight);
    }
    
    private void executeRollover(ZonedDateTime midnight) {
        try {
            LOGGER.debug("Executing scheduled rollover for: {}", midnight);
            setFile(midnight);
            scheduleNextRollover(midnight.plus(1, ChronoUnit.DAYS));
            removeOldFiles();
        } catch (Throwable t) {
            LOGGER.error("Unexpected error while moving to a new reporter file", t);
        }
    }

    /**
     * Get the "start of day" for the provided DateTime at the zone specified.
     *
     * @param now the date time to calculate from
     * @return start of the day of the date provided
     */
    private static ZonedDateTime toMidnight(ZonedDateTime now) {
        return now.toLocalDate().atStartOfDay(now.getZone()).plus(1, ChronoUnit.DAYS);
    }

    protected void removeOldFiles() {
        if (!isFileRetentionEnabled()) {
            return;
        }

        File dir = getParentDirectory();
        if (dir == null) {
            return;
        }

        String[] logFiles = dir.list();
        if (logFiles == null) {
            return;
        }

        processLogFilesForRetention(dir, logFiles);
    }
    
    private boolean isFileRetentionEnabled() {
        if (retainDays <= 0) {
            LOGGER.debug("No file retention configured");
            return false;
        }
        if (rolloverFiles == null) {
            return false;
        }
        return true;
    }
    
    private File getParentDirectory() {
        File file = new File(this.filename);
        return new File(file.getParent());
    }
    
    private void processLogFilesForRetention(File dir, String[] logFiles) {
        long currentTime = timeProvider.now().toInstant().toEpochMilli();
        
        for (String filename : logFiles) {
            if (rolloverFiles.matcher(filename).matches()) {
                processLogFile(dir, filename, currentTime);
            } else {
                LOGGER.debug("File [{}] does not match the rollover pattern", filename);
            }
        }
    }
    
    private void processLogFile(File dir, String filename, long currentTime) {
        File file = new File(dir, filename);
        if (shouldDeleteFile(file, currentTime)) {
            deleteFile(filename, file);
        } else {
            LOGGER.debug("File [{}] should not be removed", filename);
        }
    }
    
    private void deleteFile(String filename, File file) {
        LOGGER.debug("File [{}] should be removed", filename);
        boolean fileDeleted = file.delete();
        if (!fileDeleted) {
            LOGGER.warn("File [{}] has not been removed", filename);
        }
    }

    protected boolean shouldDeleteFile(File file, long currentTimeInMs) {
        return retainDays > 0 && currentTimeInMs - file.lastModified() > retainDays * MILLISECONDS_PER_DAY;
    }
}
