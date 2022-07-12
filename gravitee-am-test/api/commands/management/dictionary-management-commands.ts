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

import {getDictionaryApi} from "./service/utils";

export const createDictionary = (domainId, accessToken, body) => getDictionaryApi(accessToken).createI18nDictionary({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    body: body
});

export const getDictionary = (domainId, accessToken, dictionaryId) => getDictionaryApi(accessToken).getI18nDictionary({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    dictionary: dictionaryId
});

export const getAllDictionaries = (domainId, accessToken) => getDictionaryApi(accessToken).listI18nDictionaries({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId
});

export const updateDictionary = (domainId, accessToken, dictionaryId, body) => getDictionaryApi(accessToken).putI18nDictionary({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    dictionary: dictionaryId,
    body: body
});

/*
Differs from updateDictionary above in that only the entries can be updated.
 */

export const updateDictionaryEntries = (domainId, accessToken, dictionaryId, entryMap) => getDictionaryApi(accessToken).replaceI18nDictionaryEntries({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    dictionary: dictionaryId,
    body: entryMap
});

export const deleteDictionary = (domainId, accessToken, dictionaryId) => getDictionaryApi(accessToken).deleteI18nDictionary({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    dictionary: dictionaryId
});
