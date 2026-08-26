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
package io.gravitee.am.repository.oauth2.model;

import io.gravitee.am.common.polling.PollingRequest;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.Set;

/**
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class DeviceAuthorizationRequest implements PollingRequest {

    private String id;

    private String userCode;

    private String status;

    private String clientId;

    private String subject;

    private Set<String> scopes;

    private Date createdAt;

    private Date lastAccessAt;

    private Date expireAt;

    private int intervalIncrement;
}
