/*
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

import {getThemeApi} from "./service/utils";

export const createTheme = (domainId, accessToken, body) => getThemeApi(accessToken).createTheme({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    theme: body
});

export const getTheme = (domainId, accessToken, themeId) => getThemeApi(accessToken).getTheme({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    themeId: themeId
});

export const getAllThemes = (domainId, accessToken) => getThemeApi(accessToken).listThemes({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId
});

export const updateTheme = (domainId, accessToken, themeId, body) => getThemeApi(accessToken).updateTheme({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    themeId: themeId,
    theme: body
});

export const deleteTheme = (domainId, accessToken, themeId) => getThemeApi(accessToken).deleteTheme({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    themeId: themeId
});
