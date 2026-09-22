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
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MaterialDesignFrameworkModule } from '@ajsf/material';
import { GioMatConfigModule } from '@gravitee/ui-particles-angular';

import { ExtensionGrantFormComponent } from './form.component';

const CROSS_APP_ACCESS_SCHEMA = {
  type: 'object',
  id: 'urn:jsonschema:com:graviteesource:am:extensiongrant:xaa:XAAExtensionGrantConfiguration',
  properties: {
    grantType: { title: 'Grant Type', default: 'urn:ietf:params:oauth:grant-type:jwt-bearer', readOnly: true },
    userBindingCriteria: {
      title: 'User binding rules',
      type: 'array',
      default: [],
      items: {
        type: 'object',
        title: 'Rule',
        properties: {
          attribute: { title: 'User attribute', type: 'string' },
          expression: { title: 'Expression', type: 'string', 'x-schema-form': { 'expression-language': true } },
        },
        required: ['attribute', 'expression'],
      },
    },
  },
};

const JWT_BEARER_SCHEMA = {
  type: 'object',
  id: 'urn:jsonschema:io:gravitee:am:tokengranter:jwtbearer:JwtBearerTokenGranterConfiguration',
  properties: {
    publicKeyResolver: { title: 'Public Key resolver', type: 'string', default: 'GIVEN_KEY', enum: ['GIVEN_KEY', 'JWKS_URL'] },
    publicKey: { title: 'Resolver parameter', type: 'string' },
  },
};

describe('ExtensionGrantFormComponent', () => {
  let fixture: ComponentFixture<ExtensionGrantFormComponent>;
  let emitted: any[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, GioMatConfigModule, MaterialDesignFrameworkModule],
      declarations: [ExtensionGrantFormComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ExtensionGrantFormComponent);
    emitted = [];
    fixture.componentInstance.configurationCompleted.subscribe((wrapper) => emitted.push(wrapper));
  });

  async function render(configuration: any, schema: any): Promise<void> {
    fixture.componentRef.setInput('extensionGrantConfiguration', configuration);
    fixture.detectChanges();
    fixture.componentRef.setInput('extensionGrantSchema', schema);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('shouldNotAddPublicKeyResolverToAConfigurationWhoseSchemaDoesNotDeclareIt', async () => {
    const configuration = {};

    await render(configuration, CROSS_APP_ACCESS_SCHEMA);

    expect(configuration).not.toHaveProperty('publicKeyResolver');
    expect(emitted.length).toBeGreaterThan(0);
    emitted.forEach((wrapper) => expect(wrapper.configuration).not.toHaveProperty('publicKeyResolver'));
  });

  it('shouldNotAddPublicKeyResolverToANewGrantWhoseSchemaDoesNotDeclareIt', async () => {
    fixture.componentRef.setInput('extensionGrantSchema', {});
    fixture.detectChanges();

    await render(undefined, CROSS_APP_ACCESS_SCHEMA);

    expect(fixture.componentInstance.data).not.toHaveProperty('publicKeyResolver');
    expect(emitted.length).toBeGreaterThan(0);
    emitted.forEach((wrapper) => expect(wrapper.configuration).not.toHaveProperty('publicKeyResolver'));
  });

  it('shouldDefaultPublicKeyResolverWhenTheSchemaDeclaresIt', async () => {
    await render({}, JWT_BEARER_SCHEMA);

    expect(fixture.componentInstance.data.publicKeyResolver).toBe('GIVEN_KEY');
  });

  it('shouldDefaultPublicKeyResolverForANewGrantWithoutConfiguration', async () => {
    await render(undefined, JWT_BEARER_SCHEMA);

    expect(fixture.componentInstance.data.publicKeyResolver).toBe('GIVEN_KEY');
  });

  it('shouldKeepTheConfiguredPublicKeyResolver', async () => {
    await render({ publicKeyResolver: 'JWKS_URL', publicKey: 'https://idp.example.com/jwks' }, JWT_BEARER_SCHEMA);

    expect(fixture.componentInstance.data.publicKeyResolver).toBe('JWKS_URL');
  });

  it('shouldRenderStoredBindingRulesAsAttributeAndExpressionRows', async () => {
    await render(
      {
        userBindingCriteria: [
          { attribute: 'emails.value', expression: "{#token['email']}" },
          { attribute: 'userName', expression: "{#token['aud_sub']}" },
        ],
      },
      CROSS_APP_ACCESS_SCHEMA,
    );

    const values = Array.from(fixture.nativeElement.querySelectorAll('input')).map((input: HTMLInputElement) => input.value);
    expect(values).toEqual(expect.arrayContaining(['emails.value', "{#token['email']}", 'userName', "{#token['aud_sub']}"]));
    expect(emitted.at(-1).configuration.userBindingCriteria).toEqual([
      { attribute: 'emails.value', expression: "{#token['email']}" },
      { attribute: 'userName', expression: "{#token['aud_sub']}" },
    ]);
  });

  it('shouldEmitABindingRuleAddedAsANewRow', async () => {
    await render({}, CROSS_APP_ACCESS_SCHEMA);

    const addButton = Array.from(fixture.nativeElement.querySelectorAll('button')).find((button: HTMLButtonElement) =>
      button.textContent.includes('Add'),
    ) as HTMLButtonElement;
    addButton.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const [attribute, expression] = Array.from(fixture.nativeElement.querySelectorAll('input')) as HTMLInputElement[];
    attribute.value = 'emails.value';
    attribute.dispatchEvent(new Event('input'));
    expression.value = "{#token['email']}";
    expression.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(emitted.at(-1)).toEqual({
      isValid: true,
      configuration: { userBindingCriteria: [{ attribute: 'emails.value', expression: "{#token['email']}" }] },
    });
  });
});
