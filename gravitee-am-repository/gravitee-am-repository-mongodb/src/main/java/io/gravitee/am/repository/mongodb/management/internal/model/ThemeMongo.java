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

import io.gravitee.am.repository.mongodb.common.model.Auditable;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.Date;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThemeMongo extends Auditable {

    /**
     * Technical identifier
     */
    @BsonId
    private String id;

    private String referenceId;
    private String referenceType;
    private String logoUrl;
    private int logoWidth;
    private String faviconUrl;
    private String primaryButtonColorHex;
    private String secondaryButtonColorHex;
    private String primaryTextColorHex;
    private String secondaryTextColorHex;
    private String css;

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

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ThemeMongo that = (ThemeMongo) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}
