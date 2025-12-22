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
package io.gravitee.am.gateway.reactor.impl.transaction;

import org.springframework.beans.factory.annotation.Value;

public class TransactionHandlerFactory {
    private final static String DEFAULT_TRANSACTIONAL_ID_HEADER = "X-Gravitee-Transaction-Id";

    @Value("${handlers.request.transaction.header:" + DEFAULT_TRANSACTIONAL_ID_HEADER + "}")
    private String transactionHeader = DEFAULT_TRANSACTIONAL_ID_HEADER;

    public TransactionHandler create() {
        return new TransactionHandler(transactionHeader);
    }
}
