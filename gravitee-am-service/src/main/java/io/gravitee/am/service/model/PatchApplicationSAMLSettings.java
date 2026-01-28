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
package io.gravitee.am.service.model;

import io.gravitee.am.model.SAMLAssertionAttribute;
import io.gravitee.am.model.application.ApplicationSAMLSettings;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.List;
import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchApplicationSAMLSettings {

    private Optional<String> entityId;
    private Optional<String> attributeConsumeServiceUrl;
    private Optional<String> singleLogoutServiceUrl;
    private Optional<String> certificate;
    private Optional<Boolean> wantResponseSigned;
    private Optional<Boolean> wantAssertionsSigned;
    private Optional<String> responseBinding;
    private Optional<List<SAMLAssertionAttribute>> assertionAttributes;

    public Optional<String> getEntityId() {
        return entityId;
    }

    public void setEntityId(Optional<String> entityId) {
        this.entityId = entityId;
    }

    public Optional<String> getAttributeConsumeServiceUrl() {
        return attributeConsumeServiceUrl;
    }

    public void setAttributeConsumeServiceUrl(Optional<String> attributeConsumeServiceUrl) {
        this.attributeConsumeServiceUrl = attributeConsumeServiceUrl;
    }

    public Optional<String> getSingleLogoutServiceUrl() {
        return singleLogoutServiceUrl;
    }

    public void setSingleLogoutServiceUrl(Optional<String> singleLogoutServiceUrl) {
        this.singleLogoutServiceUrl = singleLogoutServiceUrl;
    }

    public Optional<String> getCertificate() {
        return certificate;
    }

    public void setCertificate(Optional<String> certificate) {
        this.certificate = certificate;
    }

    public Optional<Boolean> getWantResponseSigned() {
        return wantResponseSigned;
    }

    public void setWantResponseSigned(Optional<Boolean> wantResponseSigned) {
        this.wantResponseSigned = wantResponseSigned;
    }

    public Optional<Boolean> getWantAssertionsSigned() {
        return wantAssertionsSigned;
    }

    public void setWantAssertionsSigned(Optional<Boolean> wantAssertionsSigned) {
        this.wantAssertionsSigned = wantAssertionsSigned;
    }

    public Optional<String> getResponseBinding() {
        return responseBinding;
    }

    public void setResponseBinding(Optional<String> responseBinding) {
        this.responseBinding = responseBinding;
    }

    public Optional<List<SAMLAssertionAttribute>> getAssertionAttributes() {
        return assertionAttributes;
    }

    public void setAssertionAttributes(Optional<List<SAMLAssertionAttribute>> assertionAttributes) {
        this.assertionAttributes = assertionAttributes;
    }

    public ApplicationSAMLSettings patch(ApplicationSAMLSettings _toPatch) {
        // create new object for audit purpose (patch json result)
        ApplicationSAMLSettings toPatch = _toPatch == null ? new ApplicationSAMLSettings() : new ApplicationSAMLSettings(_toPatch);
        SetterUtils.safeSet(toPatch::setEntityId, this.getEntityId());
        SetterUtils.safeSet(toPatch::setAttributeConsumeServiceUrl, this.getAttributeConsumeServiceUrl());
        SetterUtils.safeSet(toPatch::setSingleLogoutServiceUrl, this.getSingleLogoutServiceUrl());
        SetterUtils.safeSet(toPatch::setCertificate, this.getCertificate());
        SetterUtils.safeSet(toPatch::setWantResponseSigned, this.getWantResponseSigned());
        SetterUtils.safeSet(toPatch::setWantAssertionsSigned, this.getWantAssertionsSigned());
        SetterUtils.safeSet(toPatch::setResponseBinding, this.getResponseBinding());
        SetterUtils.safeSet(toPatch::setAssertionAttributes, this.getAssertionAttributes());
        return toPatch;
    }
}
