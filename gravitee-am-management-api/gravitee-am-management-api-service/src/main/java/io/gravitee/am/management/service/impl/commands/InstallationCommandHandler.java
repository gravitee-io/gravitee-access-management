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

import io.gravitee.am.model.Installation;
import io.gravitee.am.service.InstallationService;
import io.gravitee.cockpit.api.command.v1.CockpitCommandType;
import io.gravitee.cockpit.api.command.v1.installation.InstallationCommand;
import io.gravitee.cockpit.api.command.v1.installation.InstallationCommandPayload;
import io.gravitee.cockpit.api.command.v1.installation.InstallationReply;
import io.gravitee.exchange.api.command.CommandHandler;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class InstallationCommandHandler implements CommandHandler<InstallationCommand, InstallationReply> {

    private final InstallationService installationService;

    @Override
    public String supportType() {
        return CockpitCommandType.INSTALLATION.name();
    }

    @Override
    public Single<InstallationReply> handle(InstallationCommand command) {
        InstallationCommandPayload installationPayload = command.getPayload();
        return installationService.getOrInitialize().map(Installation::getAdditionalInformation)
                .doOnSuccess(additionalInfos -> additionalInfos.put(Installation.COCKPIT_INSTALLATION_STATUS, installationPayload.status()))
                .flatMap(installationService::setAdditionalInformation)
                .map(installation -> new InstallationReply(command.getId()))
                .doOnSuccess(installation -> log.info("Installation status is [{}].", installationPayload.status()))
                .doOnError(error -> log.info("Error occurred when updating installation status.", error))
                .onErrorReturn(throwable -> new InstallationReply(command.getId(), throwable.getMessage()));
    }
}