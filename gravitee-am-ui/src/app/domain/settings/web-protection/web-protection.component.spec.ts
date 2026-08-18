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
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { AuthService } from '../../../services/auth.service';
import { DomainService } from '../../../services/domain.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DomainStoreService } from '../../../stores/domain.store';

jest.mock('@gravitee/ui-components/src/lib/utils', () => ({
  deepClone: (obj: any) => {
    if (obj) {
      return JSON.parse(JSON.stringify(obj));
    }
    return obj;
  },
}));

import { DomainSettingsWebProtectionComponent } from './web-protection.component';

const domainWithCsp = (csp: Record<string, unknown>) => ({
  id: 'domain-id',
  name: 'my-domain',
  webProtectionSettings: {
    csp: { inherited: false, enabled: true, reportOnly: false, scriptInlineNonce: true, ...csp },
  },
});

describe('DomainSettingsWebProtectionComponent', () => {
  let component: DomainSettingsWebProtectionComponent;
  let fixture: ComponentFixture<DomainSettingsWebProtectionComponent>;
  let domainService: { patchWebProtectionSettings: jest.Mock; notify: jest.Mock };
  let domainStore: DomainStoreService;

  const initWith = (domain: unknown) => {
    domainStore.set(domain);
    fixture = TestBed.createComponent(DomainSettingsWebProtectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(async () => {
    domainService = {
      patchWebProtectionSettings: jest.fn().mockImplementation((id, domain) => of(domain)),
      notify: jest.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [FormsModule, MatAutocompleteModule, NoopAnimationsModule],
      declarations: [DomainSettingsWebProtectionComponent],
      providers: [
        DomainStoreService,
        { provide: DomainService, useValue: domainService },
        { provide: SnackbarService, useValue: { open: jest.fn() } },
        { provide: AuthService, useValue: { hasPermissions: () => true } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    }).compileComponents();

    domainStore = TestBed.inject(DomainStoreService);
  });

  it('renders without error for a domain that overrides CSP', () => {
    initWith(domainWithCsp({ directives: ["default-src 'self'"] }));

    expect(component.cspDirectives).toEqual([{ name: 'default-src', value: "'self'" }]);
    expect(component.cspFormErrors).toEqual([]);
  });

  it('loads stored directives into editable rows', () => {
    initWith(domainWithCsp({ directives: ["default-src 'self';", 'upgrade-insecure-requests'] }));

    expect(component.cspDirectives).toEqual([
      { name: 'default-src', value: "'self'" },
      { name: 'upgrade-insecure-requests', value: '' },
    ]);
  });

  it('shows an invalid stored entry rather than dropping it, leaving the field to flag it', () => {
    initWith(domainWithCsp({ directives: ['script_src oops'] }));

    expect(component.cspDirectives).toEqual([{ name: 'script_src', value: 'oops' }]);
  });

  it('accepts a loosely-typed entry the API would also accept, hinting rather than blocking', () => {
    // "this is not valid" parses to the name "this", which is syntactically legal CSP.
    initWith(domainWithCsp({ directives: ['this is not valid'] }));

    expect(component.cspDirectives).toEqual([{ name: 'this', value: 'is not valid' }]);
    expect(component.isUnknownDirective(0)).toBe(true);
    expect(component.cspInvalid).toBe(false);
  });

  it('serializes rows back to the stored string form on save', () => {
    initWith(domainWithCsp({ directives: ["default-src 'self'"] }));

    component.cspDirectives = [
      { name: 'default-src', value: "'self'" },
      { name: 'upgrade-insecure-requests', value: '' },
    ];
    component.update();

    const patched = domainService.patchWebProtectionSettings.mock.calls[0][1];
    expect(patched.webProtectionSettings.csp.directives).toEqual(["default-src 'self'", 'upgrade-insecure-requests']);
  });

  it('adds and removes rows', () => {
    initWith(domainWithCsp({ directives: ["default-src 'self'"] }));

    component.addDirective();
    expect(component.cspDirectives).toHaveLength(2);
    expect(component.formChanged).toBe(true);

    component.removeDirective(0);
    expect(component.cspDirectives).toEqual([{ name: '', value: '' }]);
  });

  it('flags every duplicate name after the first and blocks the save', () => {
    initWith(domainWithCsp({ directives: ["default-src 'self'", "Default-Src 'none'"] }));

    expect(component.isDuplicateDirective(0)).toBe(false);
    expect(component.isDuplicateDirective(1)).toBe(true);
    expect(component.cspInvalid).toBe(true);
  });

  it('marks a stored duplicate as an error before the field is touched', () => {
    initWith(domainWithCsp({ directives: ["default-src 'self'", "default-src 'none'"] }));

    const matcher = component.duplicateErrorStateMatcher(1);

    expect(matcher.isErrorState(null, null)).toBe(true);
  });

  it('does not put an untouched, non-duplicate field into an error state', () => {
    initWith(domainWithCsp({ directives: ["default-src 'self'"] }));

    const matcher = component.duplicateErrorStateMatcher(0);

    expect(matcher.isErrorState(null, null)).toBe(false);
  });

  it('blocks the save when report only has no report target', () => {
    initWith(domainWithCsp({ reportOnly: true, directives: ["default-src 'self'"] }));

    expect(component.cspInvalid).toBe(true);
    expect(component.cspFormErrors[0]).toContain('Report only needs');
  });

  it('blocks the save when CSP is enabled with no directives', () => {
    initWith(domainWithCsp({ directives: [] }));

    expect(component.cspInvalid).toBe(true);
    expect(component.cspFormErrors[0]).toContain('at least one directive');
  });

  it('does not validate directives while CSP is inherited', () => {
    initWith({
      id: 'domain-id',
      name: 'my-domain',
      webProtectionSettings: { csp: { inherited: true, enabled: false, directives: ['this is not valid'] } },
    });

    expect(component.cspFormErrors).toEqual([]);
    expect(component.cspInvalid).toBe(false);
  });

  it('does not validate directives while CSP is disabled for the domain', () => {
    initWith(domainWithCsp({ enabled: false, directives: [] }));

    expect(component.cspFormErrors).toEqual([]);
  });

  it('flags an unrecognized directive name without blocking the save', () => {
    initWith(domainWithCsp({ directives: ["some-future-directive 'self'"] }));

    expect(component.isUnknownDirective(0)).toBe(true);
    expect(component.cspInvalid).toBe(false);
  });

  it('notes when report-to is configured without report-uri', () => {
    initWith(domainWithCsp({ directives: ["default-src 'self'", 'report-to csp-endpoint'] }));

    expect(component.needsReportingEndpoint(1)).toBe(true);
  });

  it('only requires a value for directives that take one', () => {
    initWith(domainWithCsp({ directives: ["default-src 'self'"] }));

    expect(component.isDirectiveValueRequired({ name: 'default-src', value: '' })).toBe(true);
    expect(component.isDirectiveValueRequired({ name: 'upgrade-insecure-requests', value: '' })).toBe(false);
  });

  it('disables the value field for a directive that takes no value', () => {
    initWith(domainWithCsp({ directives: ["default-src 'self'"] }));

    expect(component.isDirectiveValueAllowed({ name: 'default-src', value: '' })).toBe(true);
    expect(component.isDirectiveValueAllowed({ name: 'upgrade-insecure-requests', value: '' })).toBe(false);
    expect(component.isDirectiveValueAllowed({ name: 'sandbox', value: '' })).toBe(true);
  });

  it('clears a leftover value when the row switches to a valueless directive', () => {
    initWith(domainWithCsp({ directives: ["default-src 'self'"] }));

    const row = component.cspDirectives[0];
    row.name = 'upgrade-insecure-requests';
    component.directiveChanged(row);

    expect(row.value).toBe('');
  });

  it('keeps the value when the row switches to a directive that allows one', () => {
    initWith(domainWithCsp({ directives: ["default-src 'self'"] }));

    const row = component.cspDirectives[0];
    row.name = 'script-src';
    component.directiveChanged(row);

    expect(row.value).toBe("'self'");
  });

  it('narrows autocomplete options to what has been typed', () => {
    initWith(domainWithCsp({ directives: ["default-src 'self'"] }));

    expect(component.filteredDirectiveNames({ name: 'script', value: '' })).toContain('script-src');
    expect(component.filteredDirectiveNames({ name: 'script', value: '' })).not.toContain('default-src');
    expect(component.filteredDirectiveNames({ name: '', value: '' }).length).toBeGreaterThan(0);
  });
});
