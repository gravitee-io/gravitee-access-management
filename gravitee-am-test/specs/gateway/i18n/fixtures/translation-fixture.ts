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

import { Domain } from '@management-models/Domain';
import {
  createDomain,
  DomainOidcConfig,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createDictionary, updateDictionaryEntries } from '@management-commands/dictionary-management-commands';
import { getDomainApi } from '@management-commands/service/utils';
import { readFileSync } from 'fs';
import { join } from 'path';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export const REDIRECT_URI = 'https://callback';

/** The language the translation is written for. Not English, so the fallback case is distinguishable. */
export const TRANSLATED_LANGUAGE = 'fr';

/** Recognisable wording, so a match cannot come from the shipped bundle by coincidence. */
export const TRANSLATED_TITLE = 'AM-2184 translated sign-in title';
export const TRANSLATED_BUTTON = 'AM-2184 translated sign-in button';

/** A key of our own, not in any shipped bundle, rendered only because the template asks for it. */
export const CUSTOM_KEY = 'login.extra.text';
export const CUSTOM_WORDING = 'AM-2184 wording from a custom key';

export interface TranslationFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  oidc: DomainOidcConfig;
  clientId: string;
  /** Adds or replaces the entries on the domain's translation for TRANSLATED_LANGUAGE. */
  setTranslationEntries: (entries: Record<string, string>) => Promise<void>;
}

export const setupTranslationFixture = async (): Promise<TranslationFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;

  try {
    domain = await createDomain(accessToken, uniqueName('translation', true), 'AM-2184 a translation used on a form');

    const idpSet = await getAllIdps(domain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;

    const appName = uniqueName('translation-app', true);
    const created = await createApplication(domain.id, accessToken, {
      name: appName,
      type: 'WEB',
      clientId: appName,
      clientSecret: uniqueName('translation-secret', true),
      redirectUris: [REDIRECT_URI],
    });

    const app = await updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: [REDIRECT_URI],
            grantTypes: ['authorization_code'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
        },
        identityProviders: [{ identity: defaultIdp.id, priority: -1 }],
      },
      created.id,
    );

    const dictionary = await createDictionary(domain.id, accessToken, {
      name: uniqueName('am2184-dictionary', true),
      locale: TRANSLATED_LANGUAGE,
    });

    // A key of our own renders only if the template asks for it, so the domain's login form is
    // replaced with one that does. Overriding a shipped key needs no template change; adding a
    // new one does — that difference is what the custom-key test exists to show.
    //
    // Anything wrong here is a problem with the setup, not with the gateway, so it throws rather
    // than leaving a test to fail as though the wording had not rendered.
    const loginHtmlPath = join(
      __dirname,
      '../../../../../gravitee-am-gateway/gravitee-am-gateway-handler/gravitee-am-gateway-handler-core/src/main/resources/webroot/views/login.html',
    );
    const original = readFileSync(loginHtmlPath, 'utf-8');
    const injected = original.replace('<body>', `<body><label id="am2184-extra" th:text="#{${CUSTOM_KEY}}"></label>`);
    if (injected === original) {
      throw new Error(`No <body> found in ${loginHtmlPath}, so the custom key was never added to the template`);
    }

    await getDomainApi(accessToken).createForm({
      organizationId: process.env.AM_DEF_ORG_ID!,
      environmentId: process.env.AM_DEF_ENV_ID!,
      domain: domain.id,
      newForm: { template: 'LOGIN' as any, content: injected, enabled: true },
    });

    await startDomain(domain.id, accessToken);
    const started = await waitForDomainStart(domain);

    // No waitForSyncAfter here: a dictionary update does not reliably advance the domain's
    // lastSync, so waiting on it hangs. Callers poll the rendered page instead, which is both
    // accurate and the thing under test.
    const setTranslationEntries = async (entries: Record<string, string>) => {
      await updateDictionaryEntries(domain!.id, accessToken, dictionary.id, entries);
    };

    return {
      accessToken,
      domain: started.domain,
      oidc: started.oidcConfig,
      clientId: app.settings.oauth.clientId,
      setTranslationEntries,
      cleanUp: async () => {
        if (domain?.id) {
          await safeDeleteDomain(domain.id, accessToken);
        }
      },
    };
  } catch (error) {
    if (domain?.id) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (e) {
        console.error('Cleanup failed:', e);
      }
    }
    throw error;
  }
};
