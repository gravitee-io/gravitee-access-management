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
package io.gravitee.am.repository.mongodb.management.internal.model;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationSAMLSettingsMongo {

    private String entityId;
    private String attributeConsumeServiceUrl;
    private String singleLogoutServiceUrl;
    private String certificate;
    private boolean wantResponseSigned = true;
    private boolean wantAssertionsSigned;
    private String responseBinding;
    private List<SAMLAssertionAttributeMongo> assertionAttributes;

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getAttributeConsumeServiceUrl() {
        return attributeConsumeServiceUrl;
    }

    public void setAttributeConsumeServiceUrl(String attributeConsumeServiceUrl) {
        this.attributeConsumeServiceUrl = attributeConsumeServiceUrl;
    }

    public String getSingleLogoutServiceUrl() {
        return singleLogoutServiceUrl;
    }

    public void setSingleLogoutServiceUrl(String singleLogoutServiceUrl) {
        this.singleLogoutServiceUrl = singleLogoutServiceUrl;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public boolean isWantResponseSigned() {
        return wantResponseSigned;
    }

    public void setWantResponseSigned(boolean wantResponseSigned) {
        this.wantResponseSigned = wantResponseSigned;
    }

    public boolean isWantAssertionsSigned() {
        return wantAssertionsSigned;
    }

    public void setWantAssertionsSigned(boolean wantAssertionsSigned) {
        this.wantAssertionsSigned = wantAssertionsSigned;
    }

    public String getResponseBinding() {
        return responseBinding;
    }

    public void setResponseBinding(String responseBinding) {
        this.responseBinding = responseBinding;
    }

    public List<SAMLAssertionAttributeMongo> getAssertionAttributes() {
        return assertionAttributes;
    }

    public void setAssertionAttributes(List<SAMLAssertionAttributeMongo> assertionAttributes) {
        this.assertionAttributes = assertionAttributes;
    }
}
