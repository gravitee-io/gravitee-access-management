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
package io.gravitee.am.reporter.jdbc.audit;

import io.r2dbc.spi.R2dbcBadGrammarException;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import io.r2dbc.spi.R2dbcPermissionDeniedException;
import io.r2dbc.spi.R2dbcTimeoutException;
import io.r2dbc.spi.R2dbcTransientResourceException;
import org.junit.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.BadSqlGrammarException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class JdbcAuditReporterRetryTest {

    @Test
    public void shouldNotRetry_badGrammar() {
        assertFalse(JdbcAuditReporter.isRetryable(new BadSqlGrammarException("execute", "INSERT", new R2dbcBadGrammarException("value too long"))));
        assertFalse(JdbcAuditReporter.isRetryable(new R2dbcBadGrammarException("value too long")));
    }

    @Test
    public void shouldNotRetry_dataIntegrity() {
        assertFalse(JdbcAuditReporter.isRetryable(new DataIntegrityViolationException("duplicate", new R2dbcDataIntegrityViolationException("duplicate"))));
        assertFalse(JdbcAuditReporter.isRetryable(new R2dbcDataIntegrityViolationException("duplicate")));
    }

    @Test
    public void shouldNotRetry_permissionDenied() {
        assertFalse(JdbcAuditReporter.isRetryable(new RuntimeException("wrapper", new R2dbcPermissionDeniedException("denied"))));
    }

    @Test
    public void shouldRetry_transientErrors() {
        assertTrue(JdbcAuditReporter.isRetryable(new R2dbcTimeoutException("timeout")));
        assertTrue(JdbcAuditReporter.isRetryable(new R2dbcTransientResourceException("connection reset")));
        assertTrue(JdbcAuditReporter.isRetryable(new DataAccessResourceFailureException("connection lost")));
        assertTrue(JdbcAuditReporter.isRetryable(new IllegalStateException("unknown")));
    }
}
