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

/**
 * A map from a widget-name (e.g. 'graviteeIdentityProvider') to an array of
 * items fetched from the server.  Each item must carry at minimum `id` and
 * `name` so that a populated <select> can be rendered by AJSF.
 */
export type DynamicSourceMap = Record<string, Array<{ id: string; name: string }>>;

/**
 * Walk every top-level property in `schema.properties` and, when the property
 * carries a `widget` that matches a key in `sources`, inject `enum` +
 * `x-schema-form.titleMap` so that AJSF renders a populated <select>.
 *
 * When the matching source list is empty the property is marked `readonly`
 * (mirrors the behaviour of `factor.component.ts::applyForGraviteeResource`).
 *
 * Mutates and returns the schema so it can be used inline, exactly like the
 * factor/provider equivalents.
 */
export function applyDynamicSources(schema: any, sources: DynamicSourceMap): any {
  if (!schema?.properties) {
    return schema;
  }
  Object.keys(schema.properties).forEach((key) => {
    const property = schema.properties[key];
    applyDynamicSourceToProperty(property, sources);
  });
  return schema;
}

function applyDynamicSourceToProperty(property: any, sources: DynamicSourceMap): void {
  const widget: string | undefined = property?.widget;
  if (!widget || !(widget in sources)) {
    return;
  }
  const items = sources[widget];
  if (items?.length > 0) {
    property.enum = items.map((item) => item.id);
    property['x-schema-form'] = {
      type: 'select',
      titleMap: items.reduce<Record<string, string>>((map, item) => {
        map[item.id] = item.name;
        return map;
      }, {}),
    };
  } else {
    property['readonly'] = true;
  }
}

/**
 * Recursively walk a JSON-schema object and set `widget = 'password'` on any
 * leaf that carries `sensitive: true`.
 *
 * Extracted from the IdP-provider form's private equivalent
 * (`settings/providers/provider/form/form.component.ts::applyPasswordInputToSensitiveFields`)
 * so the CIBA device-notifier form can share one implementation.
 */
export function applyPasswordInputToSensitiveFields(schema: any): any {
  if (typeof schema !== 'object' || schema === null) {
    return schema;
  }
  for (const key in schema) {
    applyPasswordInputToSensitiveFields(schema[key]);
    if (schema[key]?.sensitive) {
      schema[key].widget = 'password';
    }
  }
  return schema;
}
