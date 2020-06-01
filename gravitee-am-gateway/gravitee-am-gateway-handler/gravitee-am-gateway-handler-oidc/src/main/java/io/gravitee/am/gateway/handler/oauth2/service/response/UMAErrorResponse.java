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
package io.gravitee.am.gateway.handler.oauth2.service.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.common.util.MultiValueMap;

import java.util.List;

/**
 * See <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#authorization-failure"></a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UMAErrorResponse {

    @JsonProperty("error")
    private String error;

    @JsonProperty("ticket")
    private String ticket;

    @JsonProperty("redirect_user")
    private String redirectUser;

    @JsonProperty("required_claims")
    private List<UMARequiredClaimsError> requiredClaims;

    @JsonProperty("interval")
    private Integer interval;

    public UMAErrorResponse(String error) {
        this.error = error;
    }

    public UMAErrorResponse setTicket(String ticket) {
        this.ticket = ticket;
        return this;
    }

    public UMAErrorResponse setRedirectUser(String redirectUser) {
        this.redirectUser = redirectUser;
        return this;
    }

    public UMAErrorResponse setRequiredClaims(List<UMARequiredClaimsError> requiredClaims) {
        this.requiredClaims = requiredClaims;
        return this;
    }

    public UMAErrorResponse setInterval(Integer interval) {
        this.interval = interval;
        return this;
    }
}
