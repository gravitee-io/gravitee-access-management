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
import io.gravitee.am.management.service.UserService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.cockpit.api.command.Command;
import io.gravitee.cockpit.api.command.CommandHandler;
import io.gravitee.cockpit.api.command.CommandStatus;
import io.gravitee.cockpit.api.command.user.UserCommand;
import io.gravitee.cockpit.api.command.user.UserPayload;
import io.gravitee.cockpit.api.command.user.UserReply;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class UserCommandHandler implements CommandHandler<UserCommand, UserReply> {

    public static final String COCKPIT_SOURCE = "cockpit";
    private final Logger logger = LoggerFactory.getLogger(UserCommandHandler.class);

    private final UserService userService;

    public UserCommandHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Command.Type handleType() {
        return Command.Type.USER_COMMAND;
    }

    @Override
    public Single<UserReply> handle(UserCommand command) {

        UserPayload userPayload = command.getPayload();

        NewUser newUser = new NewUser();
        newUser.setInternal(false);
        newUser.setExternalId(userPayload.getId());
        newUser.setUsername(userPayload.getUsername());
        newUser.setFirstName(userPayload.getFirstName());
        newUser.setLastName(userPayload.getLastName());
        newUser.setEmail(userPayload.getEmail());
        newUser.setSource(COCKPIT_SOURCE);
        newUser.setAdditionalInformation(new HashMap<>());

        if(userPayload.getAdditionalInformation() != null) {
            newUser.getAdditionalInformation().putAll(userPayload.getAdditionalInformation());
        }

        newUser.getAdditionalInformation().computeIfAbsent(StandardClaims.PICTURE, k -> userPayload.getPicture());

        return userService.createOrUpdate(ReferenceType.ORGANIZATION, userPayload.getOrganizationId(), newUser)
                .doOnSuccess(user -> logger.info("User [{}] created with id [{}].", user.getUsername(), user.getId()))
                .map(user -> new UserReply(command.getId(), CommandStatus.SUCCEEDED))
                .doOnError(error -> logger.info("Error occurred when creating user [{}] for organization [{}].", userPayload.getUsername(), userPayload.getOrganizationId(), error))
                .onErrorReturn(throwable -> new UserReply(command.getId(), CommandStatus.ERROR));
    }
}