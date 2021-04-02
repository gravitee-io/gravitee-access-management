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
import io.gravitee.cockpit.api.command.Command;
import io.gravitee.cockpit.api.command.CommandHandler;
import io.gravitee.cockpit.api.command.CommandStatus;
import io.gravitee.cockpit.api.command.goodbye.GoodbyeCommand;
import io.gravitee.cockpit.api.command.goodbye.GoodbyeReply;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

import static io.gravitee.am.model.Installation.COCKPIT_INSTALLATION_STATUS;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class GoodbyeCommandHandler implements CommandHandler<GoodbyeCommand, GoodbyeReply> {

    static final String DELETED_STATUS = "DELETED";
    private final Logger logger = LoggerFactory.getLogger(GoodbyeCommandHandler.class);

    private final InstallationService installationService;

    public GoodbyeCommandHandler(InstallationService installationService) {

        this.installationService = installationService;
    }

    @Override
    public Command.Type handleType() {
        return Command.Type.GOODBYE_COMMAND;
    }

    @Override
    public Single<GoodbyeReply> handle(GoodbyeCommand command) {
        return installationService.addAdditionalInformation(Collections.singletonMap(COCKPIT_INSTALLATION_STATUS, DELETED_STATUS))
                .flatMap(installation -> Single.just(new GoodbyeReply(command.getId(), CommandStatus.SUCCEEDED)))
                .doOnSuccess(reply -> logger.info("Installation has been removed."))
                .doOnError(error -> logger.error("Error occurred when deleting installation.", error))
                .onErrorReturn(throwable -> new GoodbyeReply(command.getId(), CommandStatus.ERROR));
    }
}
