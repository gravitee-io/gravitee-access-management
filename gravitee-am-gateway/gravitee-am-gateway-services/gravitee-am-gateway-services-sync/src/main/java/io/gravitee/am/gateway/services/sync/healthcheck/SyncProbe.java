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
package io.gravitee.am.gateway.services.sync.healthcheck;

import io.gravitee.am.gateway.services.sync.SyncManager;
import io.gravitee.node.api.healthcheck.Probe;
import io.gravitee.node.api.healthcheck.Result;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * HTTP Probe used to check if the gateway is ready to get calls.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SyncProbe implements Probe {

    @Autowired
    private SyncManager syncManager;

    @Override
    public String id() {
        return "security-domain-sync";
    }

    @Override
    public CompletionStage<Result> check() {
        if (syncManager.isAllSecurityDomainsSync()) {
            return CompletableFuture.completedFuture(Result.healthy());
        }
        return CompletableFuture.completedFuture(Result.notReady());
    }
}
