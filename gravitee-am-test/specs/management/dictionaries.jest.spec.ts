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
import fetch from "cross-fetch";
import * as faker from 'faker';
import {afterAll, beforeAll, expect} from '@jest/globals';
import {requestAdminAccessToken} from "@management-commands/token-management-commands";
import {createDomain, deleteDomain, startDomain} from "@management-commands/domain-management-commands";
import {
    createDictionary,
    deleteDictionary,
    getAllDictionaries,
    getDictionary,
    updateDictionary,
    updateDictionaryEntries
} from "@management-commands/dictionary-management-commands";

global.fetch = fetch;

let accessToken;
let domain;
let dictionary;
let i18nLanguages;
beforeAll(async () => {
    i18nLanguages = [
        {"locale": "aa", "country": "Afar"},
        {"locale":"ab", "country": "Abkhazian"},
        {"locale":"ae", "country": "Avestan"},
        {"locale":"af", "country": "Afrikaans"},
        {"locale":"ak", "country": "Akan"},
        {"locale":"am", "country": "Amharic"},
        {"locale":"an", "country": "Aragonese"},
        {"locale":"ar", "country": "Arabic"},
        {"locale":"as", "country": "Assamese"},
        {"locale":"av", "country": "Avaric"},
        {"locale":"ay", "country": "Aymara"},
        {"locale":"az", "country": "Azerbaijani"},
        {"locale":"ba", "country": "Bashkir"},
        {"locale":"be", "country": "Belarusian"},
        {"locale":"bg", "country": "Bulgarian"},
        {"locale":"bh", "country": "Bihari i18nLanguages"},
        {"locale":"bi", "country": "Bislama"},
        {"locale":"bm", "country": "Bambara"},
        {"locale":"bn", "country": "Bengali"},
        {"locale":"bo", "country": "Tibetan"}
    ];

    const adminTokenResponse = await requestAdminAccessToken();
    accessToken = adminTokenResponse.body.access_token;
    expect(accessToken).toBeDefined()

    const createdDomain = await createDomain(accessToken, "domain-applications", faker.company.catchPhraseDescriptor());
    expect(createdDomain).toBeDefined();
    expect(createdDomain.id).toBeDefined();

    const domainStarted = await startDomain(createdDomain.id, accessToken);
    expect(domainStarted).toBeDefined();
    expect(domainStarted.id).toEqual(createdDomain.id);

    domain = domainStarted;
});

async function testCreate() {
    const i18Language = i18nLanguages.pop();
    const name = i18Language.country;
    const locale = i18Language.locale;
    const created = await createDictionary(domain.id, accessToken, {name: name, "locale": locale})
    expect(created).toBeDefined();
    expect(created.id).toBeDefined();
    expect(created.name).toEqual(name);
    expect(created.locale).toEqual(locale);
    expect(created.entries).toEqual({});
    return created;
}

describe("Testing dictionaries api...", () => {
    describe("When creating many dictionaries", () => {
        const ids = [];
        for (let i = 0; i < 10; i++) {
            it('should create dictionaries with empty translations ', async () => {
                const created = await testCreate();
                ids.push(created.id)
            });
        }
        it('should find all the dictionaries', async () => {
            const dictionaries = await getAllDictionaries(domain.id, accessToken);
            expect(dictionaries).toHaveLength(ids.length);
            dictionaries.forEach(value => expect(ids).toContain(value.id));
        });
    });

    describe("When creating a sole dictionary", () => {
        it('should create a dictionary with empty translations ', async () => {
            dictionary = await testCreate();
        });
        it("should find a given dictionary", async () => {
            const found = await getDictionary(domain.id, accessToken, dictionary.id);
            expect(found).toBeDefined();
            expect(found.id).toEqual(dictionary.id);
            expect(found.name).toEqual(dictionary.name);
            expect(found.locale).toEqual(dictionary.locale);
            expect(found.entries).toEqual(dictionary.entries);
        });
    });

    describe("When updating a dictionary", () => {
        it("should update a dictionary's requested properties", async () => {
            dictionary = await testCreate();
            const beforeUpdate = await getDictionary(domain.id, accessToken, dictionary.id);
            let updateName = "updated name";
            let updateLocale = "updated locale";
            let entries = {
                "login.title": "Welcome"
            };
            await updateDictionary(domain.id, accessToken, beforeUpdate.id, {
                name: updateName,
                locale: updateLocale,
                entries: entries,
                defaultLocale: true
            });
            const afterUpdate = await getDictionary(domain.id, accessToken, beforeUpdate.id);
            expect(afterUpdate.id).toEqual(beforeUpdate.id);
            expect(afterUpdate.name).not.toEqual(beforeUpdate.name);
            expect(afterUpdate.locale).not.toEqual(beforeUpdate.locale);
            expect(afterUpdate.entries).not.toEqual(beforeUpdate.entries);
            expect(afterUpdate.name).toEqual(updateName);
            expect(afterUpdate.locale).toEqual(updateLocale);
            expect(afterUpdate.entries).toEqual(entries);
        });
    });

    describe("When updating a dictionary's entries", () => {
        it("should update a dictionary's entries", async () => {
            dictionary = await testCreate();
            const beforeUpdate = await getDictionary(domain.id, accessToken, dictionary.id);
            let entryMap = {
                "login.title": "Welcome",
                "email.title": "Email",
                "mfa.title": "MFA"
            };
            await updateDictionaryEntries(domain.id, accessToken, beforeUpdate.id, entryMap);
            const afterUpdate = await getDictionary(domain.id, accessToken, beforeUpdate.id);
            expect(afterUpdate.id).toEqual(beforeUpdate.id);
            expect(afterUpdate.name).toEqual(beforeUpdate.name);
            expect(afterUpdate.locale).toEqual(beforeUpdate.locale);
            expect(afterUpdate.entries).not.toEqual(beforeUpdate.entries);
            expect(afterUpdate.entries).toEqual(entryMap);
        });
    });

    describe("When deleting a dictionary", () => {
        it("should delete a dictionary", async () => {
            dictionary = await testCreate();
            await deleteDictionary(domain.id, accessToken, dictionary.id);

            const dictionaries = await getAllDictionaries(domain.id, accessToken);
            dictionaries.forEach(dict => expect(dict.id).not.toEqual(dictionary.id));
        });
    });
});

afterAll(async () => {
    if (domain && domain.id) {
        await deleteDomain(domain.id, accessToken);
    }
});
