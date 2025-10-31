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
import io.gravitee.am.repository.exceptions.RepositoryExceptionMapper;

/**
 * MongoDB-specific implementation of {@link RepositoryExceptionMapper}.
 * Maps MongoDB connection exceptions to {@link RepositoryConnectionException}.
 *
 * @author GraviteeSource Team
 */
public class MongoRepositoryExceptionMapper implements RepositoryExceptionMapper {

    /**
     * Checks if the throwable or its cause is a MongoDB connection exception.
     * Connection exceptions include:
     * <ul>
     *   <li>{@link MongoTimeoutException} - MongoDB operation timeout</li>
     *   <li>{@link MongoServerUnavailableException} - MongoDB server unavailable</li>
     *   <li>{@link MongoSocketOpenException} - MongoDB socket connection failure</li>
     * </ul>
     *
     * @param throwable the exception to check
     * @return true if the exception represents a connection error
     */
    @Override
    public boolean isConnectionException(Throwable throwable) {
        return isConnectionError(throwable) ||
                isConnectionError(throwable.getCause());
    }

    private static boolean isConnectionError(Throwable error) {
        return error instanceof MongoTimeoutException ||
                error instanceof MongoServerUnavailableException ||
                error instanceof MongoSocketOpenException;
    }
}

