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
import io.gravitee.cockpit.api.command.model.accesspoint.AccessPoint;
import io.gravitee.cockpit.api.command.v1.CockpitCommandType;
import io.gravitee.cockpit.api.command.v1.organization.OrganizationCommand;
import io.gravitee.cockpit.api.command.v1.organization.OrganizationCommandPayload;
import io.gravitee.cockpit.api.command.v1.organization.OrganizationReply;
import io.gravitee.exchange.api.command.CommandHandler;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationCommandHandler implements CommandHandler<OrganizationCommand, OrganizationReply> {

    private final OrganizationService organizationService;

    @Override
    public String supportType() {
        return CockpitCommandType.ORGANIZATION.name();
    }

    @Override
    public Single<OrganizationReply> handle(OrganizationCommand command) {

        OrganizationCommandPayload organizationPayload = command.getPayload();
        NewOrganization newOrganization = new NewOrganization();
        newOrganization.setHrids(organizationPayload.hrids());
        newOrganization.setName(organizationPayload.name());
        newOrganization.setDescription(organizationPayload.description());
        if (organizationPayload.accessPoints() != null) {
            newOrganization.setDomainRestrictions(organizationPayload.accessPoints()
                    .stream()
                    .filter(accessPoint -> accessPoint.getTarget() == AccessPoint.Target.GATEWAY)
                    .map(AccessPoint::getHost)
                    .collect(Collectors.toList()));
        }

        return organizationService.createOrUpdate(organizationPayload.id(), newOrganization, null)
                .map(organization -> new OrganizationReply(command.getId()))
                .doOnSuccess(reply -> log.info("Organization [{}] handled with id [{}].", organizationPayload.name(), organizationPayload.id()))
                .doOnError(error -> log.error("Error occurred when handling organization [{}] with id [{}].", organizationPayload.name(), organizationPayload.id(), error))
                .onErrorReturn(throwable -> new OrganizationReply(command.getId(), throwable.getMessage()));
    }
}
