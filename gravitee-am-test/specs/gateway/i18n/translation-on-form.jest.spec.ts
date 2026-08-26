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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { jira } from '@specs-utils/jira';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { retryUntil } from '@utils-commands/retry';
import { retryImmediatelyForThisFile, setup } from '../../test-fixture';
import {
  REDIRECT_URI,
  TRANSLATED_BUTTON,
  TRANSLATED_LANGUAGE,
  TRANSLATED_TITLE,
  TranslationFixture,
  CUSTOM_KEY,
  CUSTOM_WORDING,
  setupTranslationFixture,
} from './fixtures/translation-fixture';

setup(200000);
// Each test rewrites the translation's entries before loading the page, so they must not interleave.
retryImmediatelyForThisFile();

/**
 * AM-2184 / UC-AM66 — a translation used on a form.
 *
 * Creating a translation and saving its entries is covered elsewhere, but nothing rendered a page,
 * so a domain that stored translations and ignored them would have passed.
 *
 * `LocaleHandler` picks the language from the Accept-Language header and falls back to English,
 * which is what lets the same page be asked for in two languages and compared.
 */

/** Shipped English wording, seen when the requested language has no bundle and no translation. */
const ENGLISH_SUBMIT_BUTTON = 'Sign in';

/**
 * Shipped French wording. AM carries its own bundle per language, so an entry left out of a custom
 * translation falls back to that bundle rather than to English — the page stays in the requested
 * language and only the translated entries are replaced.
 */
const FRENCH_USERNAME_LABEL = 'Identifiant';

/** The key the sign-in button actually uses — `login.button` is not a key and has no effect. */
const BUTTON_KEY = 'login.button.submit';
const TITLE_KEY = 'login.title';

let fixture: TranslationFixture;

beforeAll(async () => {
  fixture = await setupTranslationFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

/** Loads the domain's sign-in page in the requested language and returns the rendered HTML. */
const loadSignInPage = async (language: string): Promise<string> => {
  const authorize = await performGet(
    fixture.oidc.authorization_endpoint,
    `?response_type=code&client_id=${fixture.clientId}&redirect_uri=${encodeURIComponent(REDIRECT_URI)}&scope=openid`,
    { 'Accept-Language': language },
  );

  const location = authorize.headers['location'];
  expect(location).toBeDefined();

  const url = new URL(location);
  const page = await performGet(url.origin, url.pathname + url.search, { 'Accept-Language': language });
  return page.text;
};

/** Loads the sign-in page until the gateway serves the wording just written, or gives up. */
const signInPageShowing = (language: string, expected: string): Promise<string> =>
  retryUntil(
    () => loadSignInPage(language),
    (html) => html.includes(expected),
    {
      timeoutMillis: 30000,
      intervalMillis: 500,
    },
  );

describe('A translation used on the sign-in form', () => {
  it(jira`the page shows the custom wording in the translated language ${'AM-2184'}`, async () => {
    await fixture.setTranslationEntries({ [TITLE_KEY]: TRANSLATED_TITLE, [BUTTON_KEY]: TRANSLATED_BUTTON });

    const html = await signInPageShowing(TRANSLATED_LANGUAGE, TRANSLATED_TITLE);

    expect(html).toContain(TRANSLATED_TITLE);
    expect(html).toContain(TRANSLATED_BUTTON);
  });

  it(jira`another language falls back to the default wording ${'AM-2184'}`, async () => {
    // Same domain and translation as above, only the requested language differs. Without this the
    // test above could pass on a gateway that showed the custom wording to everyone.
    const html = await loadSignInPage('de');

    expect(html).not.toContain(TRANSLATED_TITLE);
    expect(html).not.toContain(TRANSLATED_BUTTON);
    expect(html).toContain(ENGLISH_SUBMIT_BUTTON);
  });

  it(jira`wording that was not translated keeps its default ${'AM-2184'}`, async () => {
    // Only the title is translated this time; the username label is left out of the translation.
    await fixture.setTranslationEntries({ [TITLE_KEY]: TRANSLATED_TITLE });

    const html = await signInPageShowing(TRANSLATED_LANGUAGE, TRANSLATED_TITLE);

    expect(html).toContain(TRANSLATED_TITLE);
    // The untranslated entry falls back to the shipped French wording, not to English and not to
    // the raw key — the rest of the form stays in the language that was asked for.
    expect(html).toContain(FRENCH_USERNAME_LABEL);
    expect(html).not.toContain(ENGLISH_SUBMIT_BUTTON);
    expect(html).not.toContain(BUTTON_KEY);
  });

  it(jira`a key of its own is shown when the form's template asks for it ${'AM-2184'}`, async () => {
    expect(fixture.templateReferencesCustomKey).toBe(true);

    // Overriding a shipped key needs no template change; a key of your own does. This domain's
    // login form was replaced with one referencing it, which is what makes the wording appear.
    await fixture.setTranslationEntries({ [TITLE_KEY]: TRANSLATED_TITLE, [CUSTOM_KEY]: CUSTOM_WORDING });

    const html = await signInPageShowing(TRANSLATED_LANGUAGE, CUSTOM_WORDING);

    expect(html).toContain(CUSTOM_WORDING);
    // The key itself is never rendered — a missing entry would leave the label empty instead.
    expect(html).not.toContain(CUSTOM_KEY);
  });
});
