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
package io.gravitee.am.gateway.handler.common.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandResponseClassifierTest {

    @Test
    public void successResponsesAreDelivered() {
        assertEquals(CommandDeliveryStatus.DELIVERED, CommandResponseClassifier.classify(200, null));
        assertEquals(CommandDeliveryStatus.DELIVERED, CommandResponseClassifier.classify(204, ""));
    }

    @Test
    public void conflictWithIncompatibleStateIsBenign() {
        // spec-defined response of an RP that never provisioned the account
        assertEquals(CommandDeliveryStatus.UNKNOWN_ACCOUNT,
                CommandResponseClassifier.classify(409, "{\"account_state\":\"unknown\",\"error\":\"incompatible_state\"}"));
        assertEquals(CommandDeliveryStatus.UNKNOWN_ACCOUNT,
                CommandResponseClassifier.classify(409, "{\"error\":\"incompatible_state\"}"));
        assertEquals(CommandDeliveryStatus.UNKNOWN_ACCOUNT,
                CommandResponseClassifier.classify(409, "{\"account_state\":\"unknown\"}"));
    }

    @Test
    public void otherConflictsAreFailures() {
        assertEquals(CommandDeliveryStatus.FAILED, CommandResponseClassifier.classify(409, null));
        assertEquals(CommandDeliveryStatus.FAILED, CommandResponseClassifier.classify(409, "not json"));
        assertEquals(CommandDeliveryStatus.FAILED, CommandResponseClassifier.classify(409, "{\"error\":\"invalid_request\"}"));
    }

    @Test
    public void errorResponsesAreFailures() {
        assertEquals(CommandDeliveryStatus.FAILED, CommandResponseClassifier.classify(400, "{\"error\":\"invalid_request\"}"));
        assertEquals(CommandDeliveryStatus.FAILED, CommandResponseClassifier.classify(500, null));
        assertEquals(CommandDeliveryStatus.FAILED, CommandResponseClassifier.classify(503, ""));
    }
}
