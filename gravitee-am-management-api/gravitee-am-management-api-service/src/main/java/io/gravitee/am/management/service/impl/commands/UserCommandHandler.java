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

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.cockpit.api.command.v1.CockpitCommandType;
import io.gravitee.cockpit.api.command.v1.user.UserCommand;
import io.gravitee.cockpit.api.command.v1.user.UserCommandPayload;
import io.gravitee.cockpit.api.command.v1.user.UserReply;
import io.gravitee.exchange.api.command.CommandHandler;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.inject.Named;
import java.util.HashMap;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Slf4j
public class UserCommandHandler implements CommandHandler<UserCommand, UserReply> {

    public static final String COCKPIT_SOURCE = "cockpit";
    private final OrganizationUserService userService;

    public UserCommandHandler(@Named("managementOrganizationUserService") OrganizationUserService userService) {
        this.userService = userService;
    }

    @Override
    public String supportType() {
        return CockpitCommandType.USER.name();
    }

    @Override
    public Single<UserReply> handle(UserCommand command) {
        UserCommandPayload userPayload = command.getPayload();
        if (userPayload.username() == null) {
            String errorMsg = "Request rejected due to null username.";
            log.warn(errorMsg);
            return Single.just(new UserReply(command.getId(), errorMsg));
        }

        NewUser newUser = new NewUser();
        newUser.setInternal(false);
        newUser.setExternalId(userPayload.id());
        newUser.setUsername(userPayload.username());
        newUser.setFirstName(userPayload.firstName());
        newUser.setLastName(userPayload.lastName());
        newUser.setEmail(userPayload.email());
        newUser.setSource(COCKPIT_SOURCE);
        newUser.setAdditionalInformation(new HashMap<>());

        if (userPayload.additionalInformation() != null) {
            newUser.getAdditionalInformation().putAll(userPayload.additionalInformation());
        }

        newUser.getAdditionalInformation().computeIfAbsent(StandardClaims.PICTURE, k -> userPayload.picture());

        return userService.createOrUpdate(ReferenceType.ORGANIZATION, userPayload.organizationId(), newUser)
                .doOnSuccess(user -> log.info("User [{}] created with id [{}].", user.getUsername(), user.getId()))
                .map(user -> new UserReply(command.getId()))
                .doOnError(error -> log.info("Error occurred when creating user [{}] for organization [{}].", userPayload.username(), userPayload.organizationId(), error))
                .onErrorReturn(throwable -> new UserReply(command.getId(), throwable.getMessage()));
    }
}
