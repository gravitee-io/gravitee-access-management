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
package io.gravitee.am.repository.exceptions;

/**
 * Strategy interface for mapping repository-specific exceptions to {@link RepositoryConnectionException}.
 * Each repository type (JDBC, MongoDB, etc.) should provide its own implementation
 * that identifies connection-related exceptions specific to that repository technology.
 *
 * @author GraviteeSource Team
 */
public interface RepositoryExceptionMapper {

    /**
     * Checks if the given throwable or its cause represents a connection error
     * that should be wrapped as a {@link RepositoryConnectionException}.
     *
     * @param throwable the exception to check
     * @return true if the exception represents a connection error, false otherwise
     */
    boolean isConnectionException(Throwable throwable);

    /**
     * Maps the given throwable to a {@link RepositoryConnectionException} if it represents
     * a connection error, otherwise returns the original throwable.
     *
     * @param throwable the exception to map
     * @return a RepositoryConnectionException if the throwable is a connection error,
     *         otherwise the original throwable
     */
    default Throwable map(Throwable throwable) {
        if (isConnectionException(throwable)) {
            return new RepositoryConnectionException(throwable);
        }
        return throwable;
    }
}

