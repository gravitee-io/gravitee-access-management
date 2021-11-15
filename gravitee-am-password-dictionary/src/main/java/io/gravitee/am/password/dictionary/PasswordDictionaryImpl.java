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

package io.gravitee.am.password.dictionary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.sun.nio.file.SensitivityWatchEventModifier.HIGH;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.Objects.nonNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordDictionaryImpl implements PasswordDictionary, Runnable {

    /**
     * This is the default file used for the most common passwords:
     * https://github.com/danielmiessler/SecLists/blob/master/Passwords/Common-Credentials/10k-most-common.txt
     *
     * MIT License
     * Copyright (c) 2018 Daniel Miessler
     * Permission is hereby granted, free of charge, to any person obtaining a copy
     * of this software and associated documentation files (the "Software"), to deal
     * in the Software without restriction, including without limitation the rights
     * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
     * copies of the Software, and to permit persons to whom the Software is
     * furnished to do so, subject to the following conditions:
     * The above copyright notice and this permission notice shall be included in all
     * copies or substantial portions of the Software.
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
     * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
     * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
     * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
     * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
     * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
     * SOFTWARE.
     */
    private static final String DEFAULT_DICTIONARY_PATH = "/dictionaries/10k-most-common.txt";

    private static final Logger LOG = LoggerFactory.getLogger(PasswordDictionaryImpl.class);
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);


    private final Map<String, Boolean> dictionary;
    private final String filename;
    private boolean started;

    public PasswordDictionaryImpl() {
        this(null);
    }

    public PasswordDictionaryImpl(String filename) {
        this.filename = filename;
        this.dictionary = new HashMap<>();
    }

    public boolean wordExists(String word) {
        synchronized (dictionary) {
            return Boolean.TRUE.equals(dictionary.get(word));
        }
    }

    public PasswordDictionaryImpl start(boolean watch) {
        started = true;
        if (nonNull(filename) && watch) {
            executor.submit(() -> safeReadFile(filename));
            executor.submit(this);
        } else if (nonNull(filename)) {
            executor.submit(() -> safeReadFile(filename));
        } else {
            executor.submit(this::safeReadEmbeddedFile);
        }
        return this;
    }

    @Override
    public void run() {
        try {
            WatchService watcherService = FileSystems.getDefault().newWatchService();
            final File file = new File(filename);
            Path path = file.toPath();
            Path directory = path.getParent();
            directory.register(watcherService, new Kind[]{ENTRY_MODIFY}, HIGH);
            while (started) {
                var watchKey = watcherService.poll(200, TimeUnit.MILLISECONDS);
                if (nonNull(watchKey)) {
                    watchKey.pollEvents().stream()
                            .map(watchEvent -> ((WatchEvent<Path>) watchEvent).context().getFileName())
                            .filter(path.getFileName()::equals)
                            .findAny()
                            .ifPresent(__ -> this.safeReadFile(filename));
                    if (!watchKey.reset()) {
                        throw new InterruptedException("watchKey could not reset");
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Password dictionary watcher stopped. Reason:", e);
        }
    }

    private void safeReadFile(String filename) {
        try {
            LOG.info("Loading password dictionary");
            readStream(new FileInputStream(filename));
            LOG.info("Password dictionary loaded");
        } catch (Exception e) {
            LOG.warn("Password dictionary file could not be loaded, reason:", e);
        }
    }

    private void safeReadEmbeddedFile() {
        try {
            LOG.info("Loading embedded password dictionary");
            readStream(this.getClass().getResourceAsStream(DEFAULT_DICTIONARY_PATH));
            LOG.info("Embedded password dictionary loaded");
        } catch (Exception e) {
            LOG.warn("Embedded password dictionary could not be loaded, reason:", e);
        }
    }

    private void readStream(InputStream inputStream) throws IOException {
        if (nonNull(inputStream)) {
            var newEntries = new HashMap<String, Boolean>();
            var entriesToKeep = new HashSet<String>();
            var reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                if (wordExists(line)) {
                    entriesToKeep.add(line);
                } else {
                    newEntries.put(line, true);
                }
            }
            synchronized (dictionary) {
                dictionary.keySet().retainAll(entriesToKeep);
                dictionary.putAll(newEntries);
            }
        }
    }
}
