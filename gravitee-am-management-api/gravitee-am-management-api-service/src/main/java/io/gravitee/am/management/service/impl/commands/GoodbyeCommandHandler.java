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

import io.gravitee.am.service.InstallationService;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.goodbye.GoodByeCommand;
import io.gravitee.exchange.api.command.goodbye.GoodByeReply;
import io.gravitee.exchange.api.command.goodbye.GoodByeReplyPayload;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;

import static io.gravitee.am.model.Installation.COCKPIT_INSTALLATION_STATUS;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GoodbyeCommandHandler implements CommandHandler<GoodByeCommand, GoodByeReply> {

    static final String DELETED_STATUS = "DELETED";
    private final InstallationService installationService;

    @Override
    public String supportType() {
        return GoodByeCommand.COMMAND_TYPE;
    }

    @Override
    public Single<GoodByeReply> handle(GoodByeCommand command) {
        return Single.just(command.getPayload().isReconnect())
                .flatMapCompletable(isReconnect -> {
                    if (Boolean.FALSE.equals(isReconnect)) {
                        return installationService.addAdditionalInformation(Collections.singletonMap(COCKPIT_INSTALLATION_STATUS, DELETED_STATUS))
                                .ignoreElement();
                    } else {
                        return Completable.complete();
                    }
                })
                .andThen(Single.just(new GoodByeReply(command.getId(), new GoodByeReplyPayload())))
                .doOnSuccess(reply -> log.info("Installation has been removed."))
                .doOnError(error -> log.error("Error occurred when deleting installation.", error))
                .onErrorReturn(throwable -> new GoodByeReply(command.getId(), throwable.getMessage()));
    }
}
