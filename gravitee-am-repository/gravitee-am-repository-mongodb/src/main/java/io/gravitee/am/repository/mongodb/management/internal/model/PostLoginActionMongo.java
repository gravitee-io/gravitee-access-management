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

import io.gravitee.am.model.PostLoginAction;

/**
 * MongoDB representation of PostLoginAction settings.
 *
 * @author GraviteeSource Team
 */
public class PostLoginActionMongo {

    private boolean enabled;
    private boolean inherited;
    private String certificateId;
    private String url;
    private long timeout;
    private String responsePublicKey;
    private String responseTokenParam;
    private String successClaim;
    private String successValue;
    private String errorClaim;
    private String dataClaim;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

    public String getCertificateId() {
        return certificateId;
    }

    public void setCertificateId(String certificateId) {
        this.certificateId = certificateId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getResponsePublicKey() {
        return responsePublicKey;
    }

    public void setResponsePublicKey(String responsePublicKey) {
        this.responsePublicKey = responsePublicKey;
    }

    public String getResponseTokenParam() {
        return responseTokenParam;
    }

    public void setResponseTokenParam(String responseTokenParam) {
        this.responseTokenParam = responseTokenParam;
    }

    public String getSuccessClaim() {
        return successClaim;
    }

    public void setSuccessClaim(String successClaim) {
        this.successClaim = successClaim;
    }

    public String getSuccessValue() {
        return successValue;
    }

    public void setSuccessValue(String successValue) {
        this.successValue = successValue;
    }

    public String getErrorClaim() {
        return errorClaim;
    }

    public void setErrorClaim(String errorClaim) {
        this.errorClaim = errorClaim;
    }

    public String getDataClaim() {
        return dataClaim;
    }

    public void setDataClaim(String dataClaim) {
        this.dataClaim = dataClaim;
    }

    public PostLoginAction convert() {
        PostLoginAction postLoginAction = new PostLoginAction();
        postLoginAction.setEnabled(isEnabled());
        postLoginAction.setInherited(isInherited());
        postLoginAction.setCertificateId(getCertificateId());
        postLoginAction.setUrl(getUrl());
        postLoginAction.setTimeout(getTimeout());
        postLoginAction.setResponsePublicKey(getResponsePublicKey());
        postLoginAction.setResponseTokenParam(getResponseTokenParam());
        postLoginAction.setSuccessClaim(getSuccessClaim());
        postLoginAction.setSuccessValue(getSuccessValue());
        postLoginAction.setErrorClaim(getErrorClaim());
        postLoginAction.setDataClaim(getDataClaim());
        return postLoginAction;
    }

    public static PostLoginActionMongo convert(PostLoginAction postLoginAction) {
        if (postLoginAction == null) {
            return null;
        }

        PostLoginActionMongo postLoginActionMongo = new PostLoginActionMongo();
        postLoginActionMongo.setEnabled(postLoginAction.isEnabled());
        postLoginActionMongo.setInherited(postLoginAction.isInherited());
        postLoginActionMongo.setCertificateId(postLoginAction.getCertificateId());
        postLoginActionMongo.setUrl(postLoginAction.getUrl());
        postLoginActionMongo.setTimeout(postLoginAction.getTimeout());
        postLoginActionMongo.setResponsePublicKey(postLoginAction.getResponsePublicKey());
        postLoginActionMongo.setResponseTokenParam(postLoginAction.getResponseTokenParam());
        postLoginActionMongo.setSuccessClaim(postLoginAction.getSuccessClaim());
        postLoginActionMongo.setSuccessValue(postLoginAction.getSuccessValue());
        postLoginActionMongo.setErrorClaim(postLoginAction.getErrorClaim());
        postLoginActionMongo.setDataClaim(postLoginAction.getDataClaim());
        return postLoginActionMongo;
    }
}
