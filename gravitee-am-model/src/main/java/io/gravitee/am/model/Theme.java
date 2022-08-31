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
package io.gravitee.am.model;

import java.util.Date;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Theme {
    private String id;
    private String referenceId;
    private ReferenceType referenceType;
    private String logoUrl;
    private int logoWidth;
    private String faviconUrl;
    private String primaryButtonColorHex;
    private String secondaryButtonColorHex;
    private String primaryTextColorHex;
    private String secondaryTextColorHex;
    private String css;
    private Date createdAt;
    private Date updatedAt;

    public Theme() {
    }

    public Theme(Theme orign) {
        this.id = orign.id;
        this.referenceId = orign.referenceId;
        this.referenceType = orign.referenceType;
        this.logoUrl = orign.logoUrl;
        this.logoWidth = orign.logoWidth;
        this.faviconUrl = orign.faviconUrl;
        this.primaryButtonColorHex = orign.primaryButtonColorHex;
        this.secondaryButtonColorHex = orign.secondaryButtonColorHex;
        this.primaryTextColorHex = orign.primaryTextColorHex;
        this.secondaryTextColorHex = orign.secondaryTextColorHex;
        this.css = orign.css;
        this.createdAt = orign.createdAt;
        this.updatedAt = orign.updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public int getLogoWidth() {
        return logoWidth;
    }

    public void setLogoWidth(int logoWidth) {
        this.logoWidth = logoWidth;
    }

    public String getFaviconUrl() {
        return faviconUrl;
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    public String getPrimaryButtonColorHex() {
        return primaryButtonColorHex;
    }

    public void setPrimaryButtonColorHex(String primaryButtonColorHex) {
        this.primaryButtonColorHex = primaryButtonColorHex;
    }

    public String getSecondaryButtonColorHex() {
        return secondaryButtonColorHex;
    }

    public void setSecondaryButtonColorHex(String secondaryButtonColorHex) {
        this.secondaryButtonColorHex = secondaryButtonColorHex;
    }

    public String getPrimaryTextColorHex() {
        return primaryTextColorHex;
    }

    public void setPrimaryTextColorHex(String primaryTextColorHex) {
        this.primaryTextColorHex = primaryTextColorHex;
    }

    public String getSecondaryTextColorHex() {
        return secondaryTextColorHex;
    }

    public void setSecondaryTextColorHex(String secondaryTextColorHex) {
        this.secondaryTextColorHex = secondaryTextColorHex;
    }

    public String getCss() {
        return css;
    }

    public void setCss(String css) {
        this.css = css;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
