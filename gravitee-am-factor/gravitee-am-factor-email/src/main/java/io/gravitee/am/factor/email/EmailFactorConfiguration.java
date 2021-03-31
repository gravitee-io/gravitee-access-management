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
package io.gravitee.am.factor.email;

import io.gravitee.am.factor.api.FactorConfiguration;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailFactorConfiguration implements FactorConfiguration {
    private String subject;
    private String template;
    private String contentType;
    private int returnDigits;
    private int ttl;
    private boolean mfaResource;
    private String graviteeResource;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public int getReturnDigits() {
        return returnDigits;
    }

    public void setReturnDigits(int returnDigits) {
        this.returnDigits = returnDigits;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public String getGraviteeResource() {
        return graviteeResource;
    }

    public void setGraviteeResource(String graviteeResource) {
        this.graviteeResource = graviteeResource;
    }

    public boolean isMfaResource() {
        return mfaResource;
    }

    public void setMfaResource(boolean mfaResource) {
        this.mfaResource = mfaResource;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
