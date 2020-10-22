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
/*
 * Copyright 2019 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.gravitee.am.gateway.handler.vertx.auth.webauthn;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * Factory interface for creating FIDO2 MetaDataService.
 *
 * @author Paulo Lopes
 */
// TODO to remove when updating to vert.x 4
public interface MetaDataService {

    /**
     * Fetches the FIDO2 TOC for the given URL and process the entries to the metadata store.
     * Only valid entries will be stored. The operation will return {@code true} only if all
     * entries have been added. {@code false} if they have been processed but at least one was
     * invalid.
     *
     * The operation will only fail on network problems.
     *
     * @param url the url to the TOC
     * @param handler the async handler to process the response
     * @return fluent self
     */
    @Fluent
    default MetaDataService fetchTOC(String url, Handler<AsyncResult<Boolean>> handler) {
        fetchTOC(url).onComplete(handler);
        return this;
    }

    /**
     * Fetches the FIDO2 TOC for the given URL and process the entries to the metadata store.
     * Only valid entries will be stored. The operation will return {@code true} only if all
     * entries have been added. {@code false} if they have been processed but at least one was
     * invalid.
     *
     * The operation will only fail on network problems.
     *
     * @param url the url to the TOC
     * @return future result of the operation
     */
    Future<Boolean> fetchTOC(String url);

    /**
     * Manually feed a Meta Data Statement to the service.
     *
     * @param statement the json statement
     * @return fluent self
     */
    @Fluent
    MetaDataService addStatement(JsonObject statement);

    /**
     * Clears all loaded statements, both from the TOC and manually inserted.
     * The flush operation will not cancel any in-flight TOC download/processing.
     *
     * @return fluent self
     */
    @Fluent
    MetaDataService flush();
}
