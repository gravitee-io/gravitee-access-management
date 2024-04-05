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
package io.gravitee.am.management.service.impl.adapter;

import io.gravitee.am.model.Installation;
import io.gravitee.am.service.InstallationService;
import io.gravitee.cockpit.api.command.v1.CockpitCommandType;
import io.gravitee.cockpit.api.command.v1.hello.HelloReply;
import io.gravitee.exchange.api.command.CommandStatus;
import io.gravitee.exchange.api.command.ReplyAdapter;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HelloReplyAdapter implements ReplyAdapter<HelloReply, io.gravitee.exchange.api.command.hello.HelloReply> {

    private final InstallationService installationService;

    @Override
    public String supportType() {
        return CockpitCommandType.HELLO.name();
    }

    @Override
    public Single<io.gravitee.exchange.api.command.hello.HelloReply> adapt(final String targetId, final HelloReply reply) {
        return Single
                .just(reply.getPayload())
                .flatMap(replyPayload -> {
                    if (reply.getCommandStatus() == CommandStatus.SUCCEEDED) {
                        return installationService.get()
                                .map(Installation::getAdditionalInformation)
                                .doOnSuccess(infos -> infos.put(Installation.COCKPIT_INSTALLATION_ID, reply.getPayload().getInstallationId()))
                                .doOnSuccess(infos -> infos.put(Installation.COCKPIT_INSTALLATION_STATUS, reply.getPayload().getInstallationStatus()))
                                .flatMap(installationService::setAdditionalInformation)
                                .map(installation -> new io.gravitee.exchange.api.command.hello.HelloReply(reply.getCommandId(), replyPayload));
                    }
                    return Single.just(new io.gravitee.exchange.api.command.hello.HelloReply(reply.getCommandId(), reply.getErrorDetails()));
                });
    }

}
