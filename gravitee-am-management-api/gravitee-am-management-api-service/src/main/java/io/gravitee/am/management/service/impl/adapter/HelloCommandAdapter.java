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

import com.google.common.base.Strings;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Organization;
import io.gravitee.am.service.InstallationService;
import io.gravitee.cockpit.api.command.v1.CockpitCommandType;
import io.gravitee.cockpit.api.command.v1.hello.HelloCommand;
import io.gravitee.cockpit.api.command.v1.hello.HelloCommandPayload;
import io.gravitee.cockpit.api.command.v1.hello.HelloReply;
import io.gravitee.common.util.Version;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HelloCommandAdapter implements CommandAdapter<io.gravitee.exchange.api.command.hello.HelloCommand, HelloCommand, HelloReply> {

    private static final String API_URL = "API_URL";
    private static final String UI_URL = "UI_URL";

    @Value("${console.api.url:http://localhost:8093/management}")
    private String apiURL;

    @Value("${console.ui.url:http://localhost:4200}")
    private String uiURL;

    private final Node node;
    private final InstallationService installationService;

    @Override
    public String supportType() {
        return CockpitCommandType.HELLO.name();
    }

    @Override
    public Single<HelloCommand> adapt(final String targetId, final io.gravitee.exchange.api.command.hello.HelloCommand command) {
        return installationService.getOrInitialize()
                .map(installation -> {
                    HelloCommandPayload.HelloCommandPayloadBuilder<?, ?> payloadBuilder = HelloCommandPayload
                            .builder()
                            .node(
                                    io.gravitee.cockpit.api.command.model.Node
                                            .builder()
                                            .application(node.application())
                                            .installationId(installation.getId())
                                            .hostname(node.hostname())
                                            .version(Version.RUNTIME_VERSION.MAJOR_VERSION)
                                            .build()
                            )
                            .defaultOrganizationId(Organization.DEFAULT)
                            .defaultEnvironmentId(Environment.DEFAULT);
                    Map<String, String> additionalInformation = new HashMap<>(installation.getAdditionalInformation());
                    additionalInformation.put(API_URL, sanitizeUrl(apiURL));
                    additionalInformation.put(UI_URL, sanitizeUrl(uiURL));
                    payloadBuilder.additionalInformation(additionalInformation);
                    return new HelloCommand(payloadBuilder.build());
                });
    }

    private static String sanitizeUrl(String url) {
        if (Strings.isNullOrEmpty(url)) {
            return null;
        }

        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
