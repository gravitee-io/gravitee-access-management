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
package io.gravitee.am.repository.jdbc.exceptions;

import io.gravitee.am.repository.exceptions.RepositoryConnectionException;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.NonTransientDataAccessResourceException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class JdbcRepositoryExceptionMapperTest {

    private final JdbcRepositoryExceptionMapper mapper = new JdbcRepositoryExceptionMapper();

    @Test
    void shouldMapJdbcNonTransientResourceToRepositoryConnection() {
        Throwable mapped = mapper.map(new NonTransientDataAccessResourceException("jdbc down"));
        assertInstanceOf(RepositoryConnectionException.class, mapped);
    }

    @Test
    void shouldMapR2dbcNonTransientResourceToRepositoryConnection() {
        Throwable mapped = mapper.map(new R2dbcNonTransientResourceException("r2dbc down"));
        assertInstanceOf(RepositoryConnectionException.class, mapped);
    }

    @Test
    void shouldMapCauseR2dbcNonTransientResourceToRepositoryConnection() {
        Throwable mapped = mapper.map(new IllegalStateException(new R2dbcNonTransientResourceException("r2dbc cause")));
        assertInstanceOf(RepositoryConnectionException.class, mapped);
    }

    @Test
    void shouldPassThroughNonConnectionException() {
        IllegalArgumentException original = new IllegalArgumentException("not a connection error");
        Throwable mapped = mapper.map(original);
        assertSame(original, mapped);
    }

    @Test
    void shouldPassThroughWhenCauseIsNonConnectionException() {
        RuntimeException original = new RuntimeException(new IllegalArgumentException("nested not a connection error"));
        Throwable mapped = mapper.map(original);
        assertSame(original, mapped);
    }
}


