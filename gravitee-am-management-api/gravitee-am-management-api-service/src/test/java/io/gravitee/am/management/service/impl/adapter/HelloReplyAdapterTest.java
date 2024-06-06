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
import io.gravitee.cockpit.api.command.v1.hello.HelloReplyPayload;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Florent CHAMFROY (florent.chamfroy at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class HelloReplyAdapterTest {

    private static final String INSTALLATION_ID = "installation#1";
    @Mock
    private InstallationService installationService;

    private HelloReplyAdapter cut;

    @BeforeEach
    public void beforeEach() {
        cut = new HelloReplyAdapter(installationService);
    }

    @Test
    void should_support_hello() {
        assertEquals(CockpitCommandType.HELLO.name(), cut.supportType());
    }

    @Test
    void should_handle_reply() throws InterruptedException {
        HelloReply helloReply = new HelloReply(
                "commandId",
                HelloReplyPayload.builder().installationId("installation#id")
                        .installationStatus("installation#status").build()
        );

        Installation installation = new Installation();
        installation.setId(INSTALLATION_ID);
        when(installationService.get()).thenReturn(Single.just(installation));
        when(installationService.setAdditionalInformation(any())).thenReturn(Single.just(installation));
        cut
                .adapt(INSTALLATION_ID, helloReply)
                .test()
                .await()
                .assertValue(helloReplyResponse -> {
                    assertEquals(helloReply.getCommandId(), helloReplyResponse.getCommandId());
                    assertEquals(helloReply.getPayload().getTargetId(), helloReplyResponse.getPayload().getTargetId());
                    return true;
                });
        verify(installationService).setAdditionalInformation(argThat(add -> {
            assertThat(add).extracting(Installation.COCKPIT_INSTALLATION_ID, Installation.COCKPIT_INSTALLATION_STATUS)
                    .contains("installation#id", "installation#status");
            return true;
        }));
    }
}
