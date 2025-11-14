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
package io.gravitee.am.service.secrets.cache;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.gravitee.secrets.api.core.Secret;
import io.gravitee.secrets.api.core.SecretURL;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * @author GraviteeSource Team
 */
public class KryoSecureSecretsCache implements SecretsCache {

    private final Kryo kryo;
    private final Cache<SecretURL, ByteBuffer> cache;

    public KryoSecureSecretsCache(
            @Nullable Duration ttl,
            @Nullable Long maxSize
    ) {
        this.kryo = new Kryo();
        this.kryo.register(Secret.class);
        this.kryo.register(Instant.class);

        Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();
        Optional.ofNullable(ttl).ifPresent(cacheBuilder::expireAfterWrite);
        Optional.ofNullable(maxSize).ifPresent(cacheBuilder::maximumSize);

        this.cache = cacheBuilder
                .evictionListener((key, value, cause) -> clearDirectByteBuffer((ByteBuffer) value))
                .build();
    }

    @Override
    public void put(SecretURL secretURL, Secret entry) {
        final ByteBuffer byteBuffer = asByteBuffer(entry);
        cache.put(secretURL, byteBuffer);
    }

    @Override
    public Optional<Secret> get(SecretURL secretURL) {
        ByteBuffer byteBuffer = cache.getIfPresent(secretURL);
        if (byteBuffer != null) {
            return Optional.of(deserialize(byteBuffer));
        }
        return Optional.empty();
    }

    @Override
    public void triggerCleanUp() {
        cache.cleanUp();;
    }

    private Secret deserialize(ByteBuffer byteBuffer) {
        byte[] buf = new byte[byteBuffer.limit()];
        byteBuffer.position(0);
        byteBuffer.get(buf, 0, buf.length);
        try (Input in = new Input(buf)) {
            return this.kryo.readObject(in, Secret.class);
        }
    }

    private ByteBuffer asByteBuffer(Secret value) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); Output out = new Output(bytes)) {
            this.kryo.writeObject(out, value);
            byte[] result = out.toBytes();
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(result.length);
            byteBuffer.put(result);
            return byteBuffer;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void clearDirectByteBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }

        buffer.clear();
        while (buffer.hasRemaining()) {
            buffer.put((byte) 0);
        }

        buffer.clear();
    }
}
