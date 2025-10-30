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
package io.gravitee.am.repository.mongodb.exceptions;

import com.mongodb.MongoServerUnavailableException;
import com.mongodb.MongoSocketOpenException;
import com.mongodb.MongoTimeoutException;
import io.gravitee.am.repository.exceptions.RepositoryConnectionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class MongoRepositoryExceptionMapperTest {

    private final MongoRepositoryExceptionMapper mapper = new MongoRepositoryExceptionMapper();

    @Test
    void shouldMapTimeoutToRepositoryConnection() {
        Throwable mapped = mapper.map(new MongoTimeoutException("timeout"));
        assertInstanceOf(RepositoryConnectionException.class, mapped);
    }

    @Test
    void shouldMapServerUnavailableToRepositoryConnection() {
        Throwable mapped = mapper.map(new MongoServerUnavailableException("down"));
        assertInstanceOf(RepositoryConnectionException.class, mapped);
    }

    @Test
    void shouldMapSocketOpenToRepositoryConnection() {
        Throwable mapped = mapper.map(new MongoSocketOpenException("socket", null));
        assertInstanceOf(RepositoryConnectionException.class, mapped);
    }

    @Test
    void shouldMapCauseTimeoutToRepositoryConnection() {
        Throwable mapped = mapper.map(new RuntimeException(new MongoTimeoutException("timeout")));
        assertInstanceOf(RepositoryConnectionException.class, mapped);
    }

    @Test
    void shouldMapCauseServerUnavailableToRepositoryConnection() {
        Throwable mapped = mapper.map(new IllegalArgumentException(new MongoServerUnavailableException("down")));
        assertInstanceOf(RepositoryConnectionException.class, mapped);
    }

    @Test
    void shouldMapCauseSocketOpenToRepositoryConnection() {
        Throwable mapped = mapper.map(new IllegalStateException(new MongoSocketOpenException("socket", null)));
        assertInstanceOf(RepositoryConnectionException.class, mapped);
    }

    @Test
    void shouldPassThroughNonConnectionException() {
        IllegalStateException original = new IllegalStateException("other");
        Throwable mapped = mapper.map(original);
        assertSame(original, mapped);
    }

    @Test
    void shouldPassThroughWhenCauseIsNonConnectionException() {
        RuntimeException original = new RuntimeException(new IllegalArgumentException("not a connection error"));
        Throwable mapped = mapper.map(original);
        assertSame(original, mapped);
    }
}


