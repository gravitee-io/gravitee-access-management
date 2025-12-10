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
package io.gravitee.am.certificate.pkcs12;

import io.gravitee.am.certificate.api.CertificateConfiguration;
import io.gravitee.secrets.api.annotation.Secret;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Setter
@Getter
public class PKCS12Configuration implements CertificateConfiguration {

    private String content;
    @Secret
    private String storepass;
    private String alias;
    private Set<String> use;
    @Secret
    private String keypass;
    private String algorithm;

}
