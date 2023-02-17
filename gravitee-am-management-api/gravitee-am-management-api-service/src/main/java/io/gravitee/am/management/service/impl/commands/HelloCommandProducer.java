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

import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Installation;
import io.gravitee.am.model.Organization;
import io.gravitee.am.service.InstallationService;
import io.gravitee.cockpit.api.command.Command;
import io.gravitee.cockpit.api.command.CommandProducer;
import io.gravitee.cockpit.api.command.CommandStatus;
import io.gravitee.cockpit.api.command.hello.HelloCommand;
import io.gravitee.cockpit.api.command.hello.HelloReply;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("cockpitHelloCommandProducer")
public class HelloCommandProducer implements CommandProducer<HelloCommand, HelloReply> {

    private static final String API_URL = "API_URL";
    private static final String UI_URL = "UI_URL";

    @Value("${console.api.url:http://localhost:8093/management}")
    private String apiURL;

    @Value("${console.ui.url:http://localhost:4200}")
    private String uiURL;

    private final Node node;
    private final InstallationService installationService;

    public HelloCommandProducer(Node node, InstallationService installationService) {
        this.node = node;
        this.installationService = installationService;
    }

    @Override
    public Command.Type produceType() {
        return Command.Type.HELLO_COMMAND;
    }

    @Override
    public Single<HelloCommand> prepare(HelloCommand command) {

        return installationService.getOrInitialize().map(installation -> {
            command.getPayload().getNode().setInstallationId(installation.getId());
            command.getPayload().getNode().setHostname(node.hostname());
            command.getPayload().getAdditionalInformation().putAll(installation.getAdditionalInformation());
            command.getPayload().getAdditionalInformation().put(API_URL, apiURL);
            command.getPayload().getAdditionalInformation().put(UI_URL, uiURL);
            command.getPayload().setDefaultOrganizationId(Organization.DEFAULT);
            command.getPayload().setDefaultEnvironmentId(Environment.DEFAULT);

            return command;
        });
    }

    @Override
    public Single<HelloReply> handleReply(HelloReply reply) {

        if (reply.getCommandStatus() == CommandStatus.SUCCEEDED) {
            return installationService.get().
                    map(Installation::getAdditionalInformation)
                    .doOnSuccess(infos -> infos.put(Installation.COCKPIT_INSTALLATION_ID, reply.getInstallationId()))
                    .doOnSuccess(infos -> infos.put(Installation.COCKPIT_INSTALLATION_STATUS, reply.getInstallationStatus()))
                    .flatMap(installationService::setAdditionalInformation)
                    .map(installation -> reply);
        }

        return Single.just(reply);
    }
}
