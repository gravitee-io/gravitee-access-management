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
package io.gravitee.am.management.handlers.management.api.model;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Theme;

import java.util.Date;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThemeEntity {
    private String id;
    private String referenceId;
    private ReferenceType referenceType;
    private String logoUrl;
    private int logoWidth;
    private String faviconUrl;
    private String primaryButtonColorHex;
    private String secondaryButtonColorHex;
    private String primaryTextColorHex;
    private String css;
    private Date createdAt;
    private Date updatedAt;

    public ThemeEntity() {
    }

    public ThemeEntity(Theme theme) {
        this.id = theme.getId();
        this.referenceId = theme.getReferenceId();
        this.referenceType = theme.getReferenceType();
        this.css = theme.getCss();
        this.logoUrl = theme.getLogoUrl();
        this.logoWidth = theme.getLogoWidth();
        this.faviconUrl = theme.getFaviconUrl();
        this.primaryTextColorHex = theme.getPrimaryTextColorHex();
        this.primaryButtonColorHex = theme.getPrimaryButtonColorHex();
        this.secondaryButtonColorHex = theme.getSecondaryButtonColorHex();

        this.createdAt = theme.getCreatedAt();
        this.updatedAt = theme.getUpdatedAt();
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

    public Theme asTheme() {
        Theme theme = new Theme();
        theme.setId(this.id);
        theme.setReferenceId(this.referenceId);
        theme.setReferenceType(this.referenceType);
        theme.setCss(this.css);
        theme.setLogoUrl(this.logoUrl);
        theme.setLogoWidth(this.logoWidth);
        theme.setFaviconUrl(this.faviconUrl);
        theme.setPrimaryTextColorHex(this.primaryTextColorHex);
        theme.setPrimaryButtonColorHex(this.primaryButtonColorHex);
        theme.setSecondaryButtonColorHex(this.secondaryButtonColorHex);

        theme.setCreatedAt(this.createdAt);
        theme.setUpdatedAt(this.updatedAt);
        return theme;
    }
}
