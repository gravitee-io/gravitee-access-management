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
package io.gravitee.am.model.command;

import io.gravitee.am.model.ReferenceType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Durable staging entry for an OpenID Provider Command dispatch job.
 * The id is the {@link CommandRequest#getId() command id} so concurrent inserts
 * from multiple gateway nodes deduplicate on the unique key; a single node (holder
 * of the domain action lease) then processes the job with retries.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@ToString
public class CommandStaging {
    private String id;
    private String command;
    private String userId;
    private ReferenceType referenceType;
    private String referenceId;
    private int attempts;
    private boolean processed;
    /**
     * OAuth client_id values for which delivery reached a terminal state (delivered or
     * benign unknown-account); they are skipped on retry.
     */
    private List<String> terminalClientIds = new ArrayList<>();
    private Date createdAt;
    private Date updatedAt;

    public void incrementAttempts() {
        this.attempts++;
    }

    public void markAsProcessed() {
        this.processed = true;
    }

    public void markClientTerminal(String clientId) {
        if (!terminalClientIds.contains(clientId)) {
            terminalClientIds.add(clientId);
        }
    }

    public boolean isTerminal(String clientId) {
        return terminalClientIds.contains(clientId);
    }
}
