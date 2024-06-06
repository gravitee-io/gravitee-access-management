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
import io.gravitee.am.model.User;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.model.NewOrganizationUser;
import io.gravitee.cockpit.api.command.v1.CockpitCommandType;
import io.gravitee.cockpit.api.command.v1.user.UserCommand;
import io.gravitee.cockpit.api.command.v1.user.UserCommandPayload;
import io.gravitee.cockpit.api.command.v1.user.UserReply;
import io.gravitee.exchange.api.command.CommandStatus;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class UserCommandHandlerTest {

    @Mock
    private OrganizationUserService userService;

    public UserCommandHandler userCommandHandler;

    @BeforeEach
    void before() {
        userCommandHandler = new UserCommandHandler(userService);
    }

    @Test
    void support() {
        assertEquals(CockpitCommandType.USER.name(), userCommandHandler.supportType());
    }

    @Test
    void handle() {

        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("info1", "value1");
        additionalInformation.put("info2", "value2");
        UserCommandPayload userPayload = UserCommandPayload.builder()
                .id("user#1")
                .organizationId("orga#1")
                .username("Username")
                .firstName("Firstname")
                .lastName("Lastname")
                .picture("https://gravitee.io/my-picture")
                .email("email@gravitee.io")
                .additionalInformation(additionalInformation)
                .build();
        UserCommand command = new UserCommand(userPayload);


        when(userService.createOrUpdate(eq(ReferenceType.ORGANIZATION), eq("orga#1"),
                argThat(newUser -> newUser.getExternalId().equals(userPayload.id())
                        && newUser.getSource().equals("cockpit")
                        && newUser.getFirstName().equals(userPayload.firstName())
                        && newUser.getLastName().equals(userPayload.lastName())
                        && newUser.getEmail().equals(userPayload.email())
                        && newUser.getAdditionalInformation().get("info1").equals(additionalInformation.get("info1"))
                        && newUser.getAdditionalInformation().get("info2").equals(additionalInformation.get("info2"))
                        && newUser.getAdditionalInformation().get(StandardClaims.PICTURE).equals(userPayload.picture()))))
                .thenReturn(Single.just(new User()));

        TestObserver<UserReply> obs = userCommandHandler.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.SUCCEEDED));
    }

    @Test
    void handleWithException() {

        UserCommandPayload userPayload = UserCommandPayload.builder()
                .id("user#1")
                .organizationId("orga#1")
                .username("Username")
                .build();
        UserCommand command = new UserCommand(userPayload);

        when(userService.createOrUpdate(eq(ReferenceType.ORGANIZATION), eq("orga#1"), any(NewOrganizationUser.class)))
                .thenReturn(Single.error(new TechnicalException()));

        TestObserver<UserReply> obs = userCommandHandler.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
    }

    @Test
    void shouldReturnErrorWhenUsernameIsNullInPayload() {
        var command = new UserCommand(UserCommandPayload.builder().build());

        TestObserver<UserReply> obs = userCommandHandler.handle(command).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(reply -> reply.getCommandId().equals(command.getId()) && reply.getCommandStatus().equals(CommandStatus.ERROR));
    }
}
