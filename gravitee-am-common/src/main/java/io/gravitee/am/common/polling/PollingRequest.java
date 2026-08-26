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
package io.gravitee.am.common.polling;

import java.util.Date;
import java.util.Set;

/**
 * A stored authorization request the client polls the token endpoint for, such as a CIBA
 * authentication request or a device authorization request.
 *
 * @author GraviteeSource Team
 */
public interface PollingRequest {

    String getId();

    void setId(String id);

    /**
     * Flow specific status, mapped to a {@link PollingRequestState} by the owning service.
     */
    String getStatus();

    void setStatus(String status);

    String getClientId();

    void setClientId(String clientId);

    String getSubject();

    void setSubject(String subject);

    Set<String> getScopes();

    void setScopes(Set<String> scopes);

    Date getCreatedAt();

    void setCreatedAt(Date createdAt);

    /**
     * Last time the client polled for this request, used to enforce the polling interval.
     */
    Date getLastAccessAt();

    void setLastAccessAt(Date lastAccessAt);

    /**
     * Expiry of the request plus the retention window, so an expired request can still be read.
     */
    Date getExpireAt();

    void setExpireAt(Date expireAt);
}
