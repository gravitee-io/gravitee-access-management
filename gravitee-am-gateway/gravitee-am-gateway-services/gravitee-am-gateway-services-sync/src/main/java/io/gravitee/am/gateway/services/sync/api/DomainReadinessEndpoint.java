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
package io.gravitee.am.gateway.services.sync.api;

import io.gravitee.am.gateway.reactor.SecurityDomainManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.monitoring.DomainState;
import io.gravitee.am.service.EntryPointManager;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.node.api.healthcheck.Probe;
import io.gravitee.node.api.healthcheck.Result;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import io.gravitee.node.management.http.endpoint.ManagementEndpoint;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author GraviteeSource Team
 */
@CustomLog
public class DomainReadinessEndpoint implements ManagementEndpoint, Probe {

    @Autowired
    private DomainReadinessService domainReadinessService;

    @Lazy
    @Autowired
    private EntryPointManager entryPointManager;

    @Autowired
    private SecurityDomainManager securityDomainManager;

    @Override
    public String id() {
        return "domain-readiness";
    }

    @Override
    public HttpMethod method() {
        return HttpMethod.GET;
    }

    @Override
    public String path() {
        return "/domains";
    }

    @Override
    public void handle(io.vertx.ext.web.RoutingContext context) {
        String domainId = context.request().getParam("domainId");
        boolean isOutputJson = "json".equals(context.request().getParam("output"));

        if (domainId == null) {
            fetchAllDomainStates(context, isOutputJson);
            return;
        }

        fetchDomainState(context, domainId, isOutputJson);
    }

    @Override
    public CompletionStage<Result> check() {
        // Without parameter, it gives the global state of the GW (OK = all domain stable and synced)
        if (domainReadinessService.isAllDomainsReady()) {
            return CompletableFuture.completedFuture(Result.healthy());
        }
        return CompletableFuture.completedFuture(Result.notReady());
    }

    private void fetchDomainState(RoutingContext context, String domainId, boolean isOutputJson) {
        var details = domainReadinessService.getDomainState(domainId);
        if (details == null) {
            context.fail(HttpStatusCode.NOT_FOUND_404);
            return;
        }

        enrichWithEntrypoints(domainId, details);

        if (details.isSynchronized() && details.isStable()) {
            context.response().setStatusCode(HttpStatusCode.OK_200);
            if (isOutputJson) {
                context.response()
                        .putHeader("content-type", MediaType.APPLICATION_JSON)
                        .end(Json.encode(details));
            } else {
                context.response().end();
            }
        } else {
            log.debug("Domain {} is not ready (stable: {}, synchronized: {})", domainId, details.isStable(),
                    details.isSynchronized());
            context.response()
                    .setStatusCode(HttpStatusCode.SERVICE_UNAVAILABLE_503)
                    .putHeader("content-type", MediaType.APPLICATION_JSON)
                    .end(Json.encode(details));
        }
    }

    /**
     * Attach the entrypoints the gateway has cached for this domain's environment. Entrypoints are
     * environment-scoped rather than per-domain, so we resolve the domain's environment from the
     * deployed domain registry and read that environment's entrypoints from the in-memory cache.
     */
    private void enrichWithEntrypoints(String domainId, DomainState details) {
        Domain domain = securityDomainManager.get(domainId);
        if (domain == null || domain.getReferenceType() != ReferenceType.ENVIRONMENT) {
            return;
        }
        details.setEntrypoints(entryPointManager.findByEnvironmentId(domain.getReferenceId()).stream()
                .map(entrypoint -> DomainState.EntrypointRef.builder()
                        .id(entrypoint.getId())
                        .name(entrypoint.getName())
                        .url(entrypoint.getUrl())
                        .organizationId(entrypoint.getOrganizationId())
                        .environmentId(entrypoint.getEnvironmentId())
                        .build())
                .toList());
    }

    private void fetchAllDomainStates(RoutingContext context, boolean isOutputJson) {
        String content = null;
        if (isOutputJson) {
            content = Json.encode(domainReadinessService.getDomainStates());
        }

        if (domainReadinessService.isAllDomainsReady()) {
            context.response().setStatusCode(HttpStatusCode.OK_200);
        } else {
            context.response().setStatusCode(HttpStatusCode.SERVICE_UNAVAILABLE_503);
        }

        if (content != null) {
            context.response()
                    .putHeader("content-type", MediaType.APPLICATION_JSON)
                    .end(content);
        } else {
            context.response().end();
        }
    }
}
