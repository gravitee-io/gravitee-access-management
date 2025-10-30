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
import io.gravitee.am.repository.exceptions.RepositoryExceptionMapper;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import org.springframework.dao.NonTransientDataAccessResourceException;

/**
 * JDBC-specific implementation of {@link RepositoryExceptionMapper}.
 * Maps JDBC and R2DBC connection exceptions to {@link RepositoryConnectionException}.
 *
 * @author GraviteeSource Team
 */
public class JdbcRepositoryExceptionMapper implements RepositoryExceptionMapper {

    /**
     * Checks if the throwable or its cause is a JDBC/R2DBC connection exception.
     * Connection exceptions include:
     * <ul>
     *   <li>{@link NonTransientDataAccessResourceException} - Spring Data JDBC resource exceptions</li>
     *   <li>{@link R2dbcNonTransientResourceException} - R2DBC non-transient resource exceptions</li>
     * </ul>
     *
     * @param throwable the exception to check
     * @return true if the exception represents a connection error
     */
    @Override
    public boolean isConnectionException(Throwable throwable) {
        return throwable instanceof NonTransientDataAccessResourceException ||
               throwable instanceof R2dbcNonTransientResourceException ||
               (throwable != null && throwable.getCause() instanceof R2dbcNonTransientResourceException);
    }
}

