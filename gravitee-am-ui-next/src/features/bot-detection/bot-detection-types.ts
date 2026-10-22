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
export const RECAPTCHA_V3 = 'google-recaptcha-v3-am-bot-detection';

/**
 * The Angular `displayType` map of the list and step 1, with its "reCAPTHCA" typo corrected. Another plugin shows the
 * fallback: its id, or its catalogue name.
 */
const TYPE_NAMES: Record<string, string> = { [RECAPTCHA_V3]: 'Google reCAPTCHA v3' };

export function botDetectionTypeName(type: string, fallback = type): string {
    return TYPE_NAMES[type] ?? fallback;
}

/** The detection type the plugin schema declares as the default of `properties.detectionType`, as the Angular step 2 reads it. */
export function schemaDetectionType(schema: Record<string, unknown>): string | undefined {
    const properties = schema.properties as Record<string, { default?: unknown } | undefined> | undefined;
    const value = properties?.detectionType?.default;
    return typeof value === 'string' ? value : undefined;
}

/**
 * The plugin schema without `properties.detectionType`. The Angular form does not render that untyped property, so the
 * configuration it sends has no `detectionType` key. The page shows the detection type in a field of its own.
 */
export function configurationSchema(schema: Record<string, unknown>): Record<string, unknown> {
    const { detectionType: _detectionType, ...properties } = (schema.properties ?? {}) as Record<string, unknown>;
    return schema.properties ? { ...schema, properties } : schema;
}

/** The Angular `getSnippetImportJS()`. */
export function importScriptSnippet(siteKey: string): string {
    return `<script src="https://www.google.com/recaptcha/api.js?render=${siteKey}"></script>`;
}

/** The Angular `getSnippetCallService()`. */
export function callServiceSnippet(siteKey: string, tokenParameterName: string): string {
    return `<script>
function onClick(event) {
  event.preventDefault();
  grecaptcha.ready(function() {
    grecaptcha.execute('${siteKey}', {action: 'submit'}).then(function(token) {
      // Add your logic to submit
      // to your backend server here
      // and assign the token variable
      // to the configured parameter ${tokenParameterName}
    });
  });
}
</script>`;
}
