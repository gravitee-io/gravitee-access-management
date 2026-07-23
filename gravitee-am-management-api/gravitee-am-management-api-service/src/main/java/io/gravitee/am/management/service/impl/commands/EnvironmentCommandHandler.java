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
package io.gravitee.am.management.service.impl.commands;

import io.gravitee.am.common.env.CloudProperties;
import io.gravitee.am.service.EntrypointService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.model.NewEntrypoint;
import io.gravitee.am.service.model.NewEnvironment;
import io.gravitee.cockpit.api.command.model.accesspoint.AccessPoint;
import io.gravitee.cockpit.api.command.v1.CockpitCommandType;
import io.gravitee.cockpit.api.command.v1.environment.EnvironmentCommand;
import io.gravitee.cockpit.api.command.v1.environment.EnvironmentCommandPayload;
import io.gravitee.cockpit.api.command.v1.environment.EnvironmentReply;
import io.gravitee.exchange.api.command.CommandHandler;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.CustomLog;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
@Component
@CustomLog
public class EnvironmentCommandHandler implements CommandHandler<EnvironmentCommand, EnvironmentReply> {


    private final EnvironmentService environmentService;
    private final EntrypointService entrypointService;
    private final Environment environment;

    @Override
    public String supportType() {
        return CockpitCommandType.ENVIRONMENT.name();
    }

    @Override
    public Single<EnvironmentReply> handle(EnvironmentCommand command) {

        EnvironmentCommandPayload environmentPayload = command.getPayload();

        Optional<String> validationError = RequiredPayloadFields.forType("Environment")
                .string("organizationId", environmentPayload.organizationId())
                .string("id", environmentPayload.id())
                .string("name", environmentPayload.name())
                .stringList("hrids", environmentPayload.hrids())
                .validate();
        if (validationError.isPresent()) {
            log.warn(validationError.get());
            return Single.just(new EnvironmentReply(command.getId(), validationError.get()));
        }

        NewEnvironment newEnvironment = new NewEnvironment();
        newEnvironment.setHrids(environmentPayload.hrids());
        newEnvironment.setName(environmentPayload.name());
        newEnvironment.setDescription(environmentPayload.description());
        if (environmentPayload.accessPoints() != null && !CloudProperties.isManagedCloudEnabled(environment)) {
            newEnvironment.setDomainRestrictions(environmentPayload.accessPoints()
                    .stream()
                    .filter(accessPoint -> accessPoint.getTarget() == AccessPoint.Target.GATEWAY)
                    .map(AccessPoint::getHost)
                    .collect(Collectors.toList()));
        }

        return environmentService.createOrUpdate(environmentPayload.organizationId(), environmentPayload.id(), newEnvironment, null)
                .flatMap(env -> syncEntrypoints(environmentPayload).andThen(Single.just(env)))
                .map(organization -> new EnvironmentReply(command.getId()))
                .doOnSuccess(reply -> log.info("Environment [{}] handled with id [{}].", environmentPayload.name(), environmentPayload.id()))
                .doOnError(error -> log.error("Error occurred when handling environment [{}] with id [{}].", environmentPayload.name(), environmentPayload.id(), error))
                .onErrorReturn(throwable -> new EnvironmentReply(command.getId(), throwable.getMessage()));
    }

    private Completable syncEntrypoints(EnvironmentCommandPayload environmentPayload) {
        if (!CloudProperties.isManagedCloudEnabled(environment)) {
            return Completable.complete();
        }

        String organizationId = environmentPayload.organizationId();
        String environmentId = environmentPayload.id();

        List<AccessPoint> gatewayAccessPoints = environmentPayload.accessPoints() == null
                ? List.of()
                : environmentPayload.accessPoints().stream()
                        .filter(accessPoint -> accessPoint.getTarget() == AccessPoint.Target.GATEWAY)
                        .collect(Collectors.toList());

        return entrypointService.findByEnvironment(organizationId, environmentId)
                .flatMapCompletable(entrypoint -> entrypointService.delete(entrypoint.getId(), organizationId, null))
                .andThen(Completable.defer(() -> Completable.merge(gatewayAccessPoints.stream()
                        .map(accessPoint -> createEntrypoint(organizationId, environmentId, accessPoint))
                        .collect(Collectors.toList()))));
    }

    private Completable createEntrypoint(String organizationId, String environmentId, AccessPoint accessPoint) {
        NewEntrypoint newEntrypoint = new NewEntrypoint();
        newEntrypoint.setUrl("https://" + accessPoint.getHost());
        newEntrypoint.setName(accessPoint.getHost());
        newEntrypoint.setEnvironmentId(environmentId);

        return entrypointService.create(organizationId, newEntrypoint, null).ignoreElement();
    }
}
