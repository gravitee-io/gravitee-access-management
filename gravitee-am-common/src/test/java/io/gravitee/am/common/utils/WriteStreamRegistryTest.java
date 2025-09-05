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
package io.gravitee.am.common.utils;

import io.vertx.core.streams.WriteStream;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class WriteStreamRegistryTest {

    @Test
    public void write_stream_registry_test(){
        WriteStreamRegistry registry = new WriteStreamRegistry();

        assertEquals(0, registry.writeStreams.size());
        assertEquals(0, registry.writeStreamsUsage.size());

        WriteStream stream = writeStream();
        registry.getOrCreate("id", () -> stream);

        assertEquals(1, registry.writeStreams.size());
        assertEquals(1, registry.writeStreamsUsage.size());
        assertEquals(1, registry.writeStreamsUsage.get("id").get());

        registry.getOrCreate("id", () -> stream);

        assertEquals(1, registry.writeStreams.size());
        assertEquals(1, registry.writeStreamsUsage.size());
        assertEquals(2, registry.writeStreamsUsage.get("id").get());

        WriteStream stream2 = writeStream();
        registry.getOrCreate("id2", () -> stream2);

        assertEquals(2, registry.writeStreams.size());
        assertEquals(2, registry.writeStreamsUsage.size());
        assertEquals(1, registry.writeStreamsUsage.get("id2").get());

        Optional<WriteStream> optStream = registry.decreaseUsage("id2");
        assertTrue(optStream.isPresent());
        assertSame(optStream.get(), stream2);
        assertEquals(1, registry.writeStreams.size());
        assertEquals(1, registry.writeStreamsUsage.size());
        assertNull(registry.writeStreamsUsage.get("id2"));

        assertFalse(registry.decreaseUsage("id").isPresent());
        assertEquals(1, registry.writeStreams.size());
        assertEquals(1, registry.writeStreamsUsage.size());
        assertEquals(1, registry.writeStreamsUsage.get("id").get());
    }

    private WriteStream writeStream() {
        return Mockito.mock(WriteStream.class);
    }

}