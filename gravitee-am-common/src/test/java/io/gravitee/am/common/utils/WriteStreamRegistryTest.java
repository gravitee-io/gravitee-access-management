package io.gravitee.am.common.utils;

import io.vertx.core.streams.WriteStream;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class WriteStreamRegistryTest {

    @Test
    public void write_stream_registry_test(){
        WriteStreamRegistry registry = new WriteStreamRegistry();

        assertEquals(0, registry.writeStreams.size());
        assertEquals(0, registry.writeStreamsUsage.size());

        registry.add("id", this::writeStream);

        assertEquals(1, registry.writeStreams.size());
        assertEquals(1, registry.writeStreamsUsage.size());
        assertEquals(1, registry.writeStreamsUsage.get("id").get());

        registry.add("id", this::writeStream);

        assertEquals(1, registry.writeStreams.size());
        assertEquals(1, registry.writeStreamsUsage.size());
        assertEquals(2, registry.writeStreamsUsage.get("id").get());

        registry.add("id2", this::writeStream);

        assertEquals(2, registry.writeStreams.size());
        assertEquals(2, registry.writeStreamsUsage.size());
        assertEquals(1, registry.writeStreamsUsage.get("id2").get());

        assertTrue(registry.decreaseUsage("id2").isPresent());
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