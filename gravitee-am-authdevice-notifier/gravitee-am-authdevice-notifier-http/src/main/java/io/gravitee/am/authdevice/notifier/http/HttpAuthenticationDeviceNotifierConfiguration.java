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
package io.gravitee.am.authdevice.notifier.http;

import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierConfiguration;
import io.gravitee.common.http.HttpHeader;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Setter
@Getter
public class HttpAuthenticationDeviceNotifierConfiguration implements AuthenticationDeviceNotifierConfiguration {

    private String headerName = "Authorization";
    private String headerValue;
    private String endpoint;
    private Integer connectTimeout = 5000;
    private Integer idleTimeout = 10000;
    private Integer maxPoolSize = 10;
    private List<HttpHeader> httpHeaders;

}
