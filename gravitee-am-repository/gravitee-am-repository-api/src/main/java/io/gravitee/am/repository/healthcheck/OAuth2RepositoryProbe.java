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
package io.gravitee.am.repository.healthcheck;

import io.gravitee.am.repository.oauth2.api.BackwardCompatibleTokenRepository;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.node.api.healthcheck.Probe;
import io.gravitee.node.api.healthcheck.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.concurrent.CompletableFuture;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2RepositoryProbe implements Probe {

    private static final String TOKEN = "token";

    @Lazy
    @Autowired
    private BackwardCompatibleTokenRepository tokenRepository;

    @Override
    public String id() {
        return "oauth2-repository";
    }

    @Override
    public CompletableFuture<Result> check() {
        // Search for an oauth 2.0 token to check repository connection
        final CompletableFuture<Result> future = new CompletableFuture<>();

        tokenRepository.findAccessTokenByJti(TOKEN)
                .subscribe(
                        domain -> future.complete(Result.healthy()),
                        error -> future.complete(Result.unhealthy(error)),
                        () -> future.complete(Result.healthy())); // repository is up but returned no result

        return future;
    }
}
