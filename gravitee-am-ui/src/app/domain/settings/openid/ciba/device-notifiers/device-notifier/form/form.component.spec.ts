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
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { DeviceNotifierFormComponent } from './form.component';

describe('DeviceNotifierFormComponent', () => {
  let component: DeviceNotifierFormComponent;
  let fixture: ComponentFixture<DeviceNotifierFormComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [DeviceNotifierFormComponent],
      teardown: { destroyAfterEach: false },
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DeviceNotifierFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('applyDynamicSources', () => {
    it('injects enum and titleMap for a graviteeIdentityProvider property when IdPs are present', () => {
      const schema = {
        properties: {
          identityProviderId: { widget: 'graviteeIdentityProvider', type: 'string' },
        },
      };
      const idps = [{ id: 'idp-1', name: 'Auth0' }];

      const result = component.applyDynamicSources(schema, { graviteeIdentityProvider: idps });

      const prop = result.properties.identityProviderId;
      expect(prop.enum).toEqual(['idp-1']);
      expect(prop['x-schema-form']).toEqual({ type: 'select', titleMap: { 'idp-1': 'Auth0' } });
    });

    it('marks the property readonly when IdP list is empty', () => {
      const schema = {
        properties: {
          identityProviderId: { widget: 'graviteeIdentityProvider', type: 'string' },
        },
      };

      const result = component.applyDynamicSources(schema, { graviteeIdentityProvider: [] });

      const prop = result.properties.identityProviderId;
      expect(prop['readonly']).toBe(true);
      expect(prop.enum).toBeUndefined();
      expect(prop['x-schema-form']).toBeUndefined();
    });

    it('leaves properties without a matching widget untouched', () => {
      const schema = {
        properties: {
          someField: { type: 'string' },
        },
      };

      const result = component.applyDynamicSources(schema, { graviteeIdentityProvider: [{ id: 'idp-1', name: 'Auth0' }] });

      const prop = result.properties.someField;
      expect(prop.enum).toBeUndefined();
      expect(prop['x-schema-form']).toBeUndefined();
    });
  });

  describe('applyPasswordInputToSensitiveFields', () => {
    it('sets widget to password for a sensitive field', () => {
      const schema = {
        properties: {
          secret: { type: 'string', sensitive: true },
          other: { type: 'string' },
        },
      };

      const result = component.applyPasswordInputToSensitiveFields(JSON.parse(JSON.stringify(schema)));

      expect(result.properties.secret.widget).toBe('password');
      expect(result.properties.other.widget).toBeUndefined();
    });

    it('masks sensitive fields nested inside array item properties', () => {
      const schema = {
        properties: {
          items: {
            type: 'array',
            items: {
              properties: {
                token: { type: 'string', sensitive: true },
              },
            },
          },
        },
      };

      const result = component.applyPasswordInputToSensitiveFields(JSON.parse(JSON.stringify(schema)));

      expect(result.properties.items.items.properties.token.widget).toBe('password');
    });
  });
});
