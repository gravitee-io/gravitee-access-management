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
package io.gravitee.am.management.handlers.internalapi.endpoints;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.service.telemetry.DomainPassRunner;
import io.gravitee.am.management.service.telemetry.SummaryReportCollector;
import io.gravitee.am.service.InstallationService;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import io.vertx.ext.web.RoutingContext;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Returns the reports the node would send now: {@code GET /_node/telemetry}.
 * <p>
 * It sends nothing, and it answers while telemetry is disabled, so an operator can read every field
 * before they decide whether to leave the feature on.
 *
 * @author GraviteeSource Team
 */
public class TelemetryEndpoint extends AbstractInternalApiEndpoint {

    private final SummaryReportCollector summaryReportCollector;
    private final DomainPassRunner domainPassRunner;
    private final InstallationService installationService;

    public TelemetryEndpoint(
        SummaryReportCollector summaryReportCollector,
        DomainPassRunner domainPassRunner,
        InstallationService installationService,
        ObjectMapper objectMapper
    ) {
        super(objectMapper);
        this.summaryReportCollector = summaryReportCollector;
        this.domainPassRunner = domainPassRunner;
        this.installationService = installationService;
    }

    @Override
    public HttpMethod method() {
        return HttpMethod.GET;
    }

    @Override
    public String path() {
        return "/telemetry";
    }

    @Override
    public void handle(RoutingContext context) {
        Single
            .zip(
                summaryReportCollector.collect(),
                installationService.get().flatMap(installation -> domainPassRunner.preview(installation.getId())),
                (summary, domainsPreview) -> {
                    final Map<String, Object> body = new LinkedHashMap<>();
                    body.put("summary", summary);
                    body.put("domainsPreview", domainsPreview);
                    return body;
                }
            )
            .subscribe(
                body -> respond(context, HttpStatusCode.OK_200, body),
                throwable -> respondFailure(context, throwable, "Unable to build the telemetry preview")
            );
    }
}
