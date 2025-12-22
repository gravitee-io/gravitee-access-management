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
package io.gravitee.am.service.secrets.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KryoPoolTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();
    ExecutorService executor = Executors.newFixedThreadPool(4);

    @Test
    public void shouldUtilizeKryoPool() throws Exception {
        int poolSize = 2;
        AtomicInteger kryosInstances = new AtomicInteger(0);

        KryoPool kryoPool = new KryoPool(poolSize, Duration.ofSeconds(1), () -> {
            Kryo kryo = new Kryo();
            kryo.register(Instant.class);
            kryosInstances.incrementAndGet();
            return kryo;
        });

        Instant now = Instant.now();
        File timestampFile = tempDir.newFile("timestamp.dat");

        try(var output = new Output(new FileOutputStream(timestampFile))){
            kryoPool.doWithKryo(kryo -> kryo.writeClassAndObject(output, now));
        }

        AtomicInteger counter = new AtomicInteger(0);
        int testSize = 100;
        for(int i = 0; i < testSize; i++) {
            executor.submit(() -> {
                try(var input = new Input(new FileInputStream(timestampFile))){
                    Object o = kryoPool.withKryo(kryo -> kryo.readClassAndObject(input));
                    Assertions.assertEquals(now, o);
                    counter.incrementAndGet();
                } catch (Exception e){
                    Assertions.fail(e);
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);
        Thread.sleep(1000);
        assertTrue(kryosInstances.get() <= kryoPool.queue.size());
        assertEquals(testSize, counter.get());
    }

    @Test
    public void shouldPreventExceedTheLimit() throws Exception {
        int poolSize = 1;
        AtomicInteger kryosInstances = new AtomicInteger(0);

        KryoPool kryoPool = new KryoPool(poolSize, Duration.ofSeconds(1), () -> {
            Kryo kryo = new Kryo();
            kryo.register(Instant.class);
            kryosInstances.incrementAndGet();
            return kryo;
        });

        Instant now = Instant.now();
        File timestampFile = tempDir.newFile("timestamp2.dat");

        try(var output = new Output(new FileOutputStream(timestampFile))){
            kryoPool.doWithKryo(kryo -> kryo.writeClassAndObject(output, now));
        }

        // long read
        executor.submit(() -> {
            try(var input = new Input(new FileInputStream(timestampFile))){
                Object o = kryoPool.withKryo(kryo -> {
                    try {
                        Thread.sleep(3000);
                        return kryo.readClassAndObject(input);
                    } catch (Exception e){
                        throw new RuntimeException(e);
                    }
                });
                Assertions.assertEquals(now, o);
            } catch (Exception e){
                Assertions.fail(e);
            }
        });

        // next read
        executor.submit(() -> {
            try(var input = new Input(new FileInputStream(timestampFile))){
                Object o = kryoPool.withKryo(kryo -> kryo.readClassAndObject(input));
                Assertions.assertEquals(now, o);
            } catch (Exception e){
                Assertions.fail(e);
            }
        });
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);
        Thread.sleep(1000);
        assertEquals(1, kryoPool.queue.size());
        assertEquals(2, kryosInstances.get()); // first deserialization takes 3 seconds and the lock duration is 1 sec. Therefore, two Kryo instances should be created.
    }

}