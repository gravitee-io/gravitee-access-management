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

import io.gravitee.common.utils.UUID;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * A {@link Handler} used to set the transaction ID of the request and the response.
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TransactionHandler implements Handler<RoutingContext> {

    final static String DEFAULT_TRANSACTIONAL_ID_HEADER = "X-Transaction-Id";

    private String transactionHeader = DEFAULT_TRANSACTIONAL_ID_HEADER;

    TransactionHandler() {
    }

    TransactionHandler(String transactionHeader) {
        this.transactionHeader = transactionHeader;
    }

    @Override
    public void handle(RoutingContext context) {
        String transactionId = context.request().headers().get(transactionHeader);

        if (transactionId == null) {
            transactionId = UUID.toString(UUID.random());
            context.request().headers().set(transactionHeader, transactionId);
        }
        context.response().headers().set(transactionHeader,transactionId);

        context.next();
    }
}
