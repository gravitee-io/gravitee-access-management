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
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

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

    private static Timer __rollover;

    private final SimpleDateFormat fileDateFormat = new SimpleDateFormat(ROLLOVER_FILE_DATE_FORMAT);

    private long retainDays = -1;

    private final Pattern rolloverFiles;

    private TimeProvider timeProvider = new TimeProvider() {};

    /**
     * Interface to provide the current time and time in a specific time zone.
     */
    protected interface TimeProvider {
        default ZonedDateTime now() {
            return ZonedDateTime.now();
        }
        default ZonedDateTime now(TimeZone timeZone) {
            return ZonedDateTime.now(timeZone.toZoneId());
        }
    }

    protected VertxFileWriter(Vertx vertx, Formatter<T> formatter, String filename, long retainDays, TimeProvider timeProvider) throws IOException {
        this(vertx, formatter, filename, retainDays);
        this.timeProvider = timeProvider;
    }

    public VertxFileWriter(Vertx vertx, Formatter<T> formatter, String filename) throws IOException {
        this.vertx = vertx;
        this.formatter = formatter;

        if (filename != null) {
            filename = filename.trim();
            if (filename.length() == 0)
                filename = null;
        }

        if (filename == null) {
            throw new IllegalArgumentException("Invalid filename");
        }

        this.filename = filename;

        // calculate the rollover files pattern
        File file = new File(this.filename);
        templateFilename = file.getName();

        rolloverFiles = Pattern.compile(templateFilename.replaceFirst(YYYY_MM_DD, "([0-9]{4}_[0-9]{2}_[0-9]{2})"));

        __rollover = new Timer(VertxFileWriter.class.getName(), true);
    }

    public VertxFileWriter(Vertx vertx, Formatter<T> formatter, String filename, long retainDays) throws IOException {
        this(vertx, formatter, filename);
        this.retainDays = retainDays;
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
            // Check directory
            File file = new File(filename);
            try {
                filename = file.getCanonicalPath();

                file = new File(filename);
                File dir = new File(file.getParent());
                if (!dir.isDirectory() || !dir.canWrite()) {
                    LOGGER.error("Cannot write reporter data to directory {}", dir);
                    promise.fail(new IOException("Cannot write reporter data to directory " + dir));
                    return promise.future();
                }

                String newDate = fileDateFormat.format(new Date(now.toInstant().toEpochMilli()));
                filename = dir.getAbsolutePath() + File.separatorChar + 
                          templateFilename.replaceFirst("(?i)" + YYYY_MM_DD, newDate);

                LOGGER.info("Initializing file reporter to write into file: {}", filename);

                AsyncFile oldAsyncFile = asyncFile;

                vertx.fileSystem().open(filename, new OpenOptions()
                                .setAppend(true)
                                .setCreate(true)
                                .setDsync(true), event -> {
                            if (event.succeeded()) {
                                asyncFile = event.result();

                                if (oldAsyncFile != null) {
                                    // Now we can close previous file safely
                                    close(oldAsyncFile).onFailure(throwable -> {
                                        LOGGER.error("An error occurs while closing file writer [{}]", this.filename, throwable);
                                    });
                                }

                                promise.complete();
                            } else {
                                LOGGER.error("An error occurs while starting file writer [{}]", this.filename, event.cause());
                                promise.fail(event.cause());
                            }
                        }
                );
            } catch (IOException ioe) {
                promise.fail(ioe);
            }
        }

        return promise.future();
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
        RollTask rollTask = new RollTask();

        // Get tomorrow's midnight based on Configured TimeZone
        ZonedDateTime midnight = toMidnight(now);

        // Schedule next rollover event to occur, based on local machine's Unix Epoch milliseconds
        long delay = midnight.toInstant().toEpochMilli() - now.toInstant().toEpochMilli();
        LOGGER.debug("Scheduling next rollover for {} in {} milliseconds", midnight, delay);
        synchronized (VertxFileWriter.class) {
            __rollover.schedule(rollTask, delay);
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

    private class RollTask extends TimerTask {
        @Override
        public void run() {
            try {
                ZonedDateTime now = timeProvider.now(fileDateFormat.getTimeZone());
                VertxFileWriter.this.setFile(now);
                VertxFileWriter.this.scheduleNextRollover(now);
                VertxFileWriter.this.removeOldFiles();
            } catch (Throwable t) {
                LOGGER.error("Unexpected error while moving to a new reporter file", t);
            }
        }
    }

    protected void removeOldFiles() {
        if (retainDays <= 0) {
            LOGGER.debug("No file retention configured");
            return;
        }

        if (rolloverFiles == null) {
            return;
        }

        long now = System.currentTimeMillis();
        File file = new File(this.filename);


        File dir = new File(file.getParent());
        String[] logList = dir.list();
        if (logList == null) {
            return;
        }

        for (String fn : logList) {
            LOGGER.debug("Checking file [{}]", fn);
            if (rolloverFiles.matcher(fn).matches()) {
                File f = new File(dir, fn);
                if (shouldDeleteFile(f, now)) {
                    LOGGER.debug("File [{}] should be removed", fn);
                    boolean fileDeleted = f.delete();
                    if (!fileDeleted) {
                        LOGGER.warn("File [{}] has not been removed", fn);
                    }
                } else {
                    LOGGER.debug("File [{}] should not be removed", fn);
                }
            } else {
                LOGGER.debug("File [{}] does not match the rollover pattern", fn);
            }
        }
    }

    protected boolean shouldDeleteFile(File file, long currentTimeInMs) {
        return retainDays > 0 && currentTimeInMs - file.lastModified() > retainDays * 1000 * 60 * 60 * 24;
    }
}
