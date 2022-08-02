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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.gravitee.am.model.IUser;
import io.gravitee.am.model.ReferenceType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Date;
import java.util.Map;

import static io.gravitee.am.service.validators.email.EmailValidatorImpl.EMAIL_MAX_LENGTH;


/**
 * @@author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Teams
 */
public class NewTheme  {
    private String logoUrl;
    private int logoWidth;
    private String faviconUrl;
    private String primaryButtonColorHex;
    private String secondaryButtonColorHex;
    private String primaryTextColorHex;
    private String css;

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
}
