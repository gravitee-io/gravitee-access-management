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

import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.model.NewOrganization;
import io.gravitee.cockpit.api.command.Command;
import io.gravitee.cockpit.api.command.CommandHandler;
import io.gravitee.cockpit.api.command.CommandStatus;
import io.gravitee.cockpit.api.command.organization.OrganizationCommand;
import io.gravitee.cockpit.api.command.organization.OrganizationPayload;
import io.gravitee.cockpit.api.command.organization.OrganizationReply;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class OrganizationCommandHandler implements CommandHandler<OrganizationCommand, OrganizationReply> {

    private final Logger logger = LoggerFactory.getLogger(OrganizationCommandHandler.class);

    private final OrganizationService organizationService;

    public OrganizationCommandHandler(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @Override
    public Command.Type handleType() {
        return Command.Type.ORGANIZATION_COMMAND;
    }

    @Override
    public Single<OrganizationReply> handle(OrganizationCommand command) {

        OrganizationPayload organizationPayload = command.getPayload();
        NewOrganization newOrganization = new NewOrganization();
        newOrganization.setHrids(organizationPayload.getHrids());
        newOrganization.setName(organizationPayload.getName());
        newOrganization.setDescription(organizationPayload.getDescription());
        newOrganization.setDomainRestrictions(organizationPayload.getDomainRestrictions());

        return organizationService.createOrUpdate(organizationPayload.getId(), newOrganization, null)
                .map(organization -> new OrganizationReply(command.getId(), CommandStatus.SUCCEEDED))
                .doOnSuccess(reply -> logger.info("Organization [{}] handled with id [{}].", organizationPayload.getName(), organizationPayload.getId()))
                .doOnError(error -> logger.error("Error occurred when handling organization [{}] with id [{}].", organizationPayload.getName(), organizationPayload.getId(), error))
                .onErrorReturn(throwable -> new OrganizationReply(command.getId(), CommandStatus.ERROR));
    }
}