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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.common.utils.UUID;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import lombok.RequiredArgsConstructor;

/**
 * A {@link Handler} used to set the transaction ID of the request and the response.
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
public class TransactionHandler implements Handler<RoutingContext> {
    private final String transactionHeader;

    @Override
    public void handle(RoutingContext context) {
        String transactionId = context.request().headers().get(transactionHeader);

        if (transactionId == null) {
            transactionId = UUID.toString(UUID.random());
            context.request().headers().set(transactionHeader, transactionId);
        }
        context.response().headers().set(transactionHeader, transactionId);
        context.put(ConstantKeys.TRANSACTION_ID_KEY, transactionId);

        context.next();
    }

}
