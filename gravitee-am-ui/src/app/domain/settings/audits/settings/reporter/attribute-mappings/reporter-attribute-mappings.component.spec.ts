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
import { NO_ERRORS_SCHEMA, SimpleChange } from '@angular/core';
import { of } from 'rxjs';

import { OrganizationService } from '../../../../../../services/organization.service';

import { ReporterAttributeMappingsComponent } from './reporter-attribute-mappings.component';
import { AttributeMappingsChange, AttributeMappingsSeed, MAX_ATTRIBUTE_MAPPINGS } from './reporter-attribute-mappings.types';

describe('ReporterAttributeMappingsComponent', () => {
  let component: ReporterAttributeMappingsComponent;
  let fixture: ComponentFixture<ReporterAttributeMappingsComponent>;
  let emitted: AttributeMappingsChange[];

  beforeEach(async () => {
    const organizationService = {
      auditEventTypes: jest.fn().mockReturnValue(of(['USER_LOGIN', 'USER_LOGOUT'])),
    } as unknown as OrganizationService;

    await TestBed.configureTestingModule({
      declarations: [ReporterAttributeMappingsComponent],
      providers: [{ provide: OrganizationService, useValue: organizationService }],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(ReporterAttributeMappingsComponent);
    component = fixture.componentInstance;
    emitted = [];
    component.attributeMappingsChanged.subscribe((change) => emitted.push(change));
  });

  function seed(value: AttributeMappingsSeed) {
    component.seed = value;
    component.ngOnChanges({ seed: new SimpleChange(null, value, true) });
  }

  it('loads the event types on init', () => {
    component.ngOnInit();

    expect(component.allEventTypes).toEqual(['USER_LOGIN', 'USER_LOGOUT']);
  });

  it('builds a row per seeded mapping', () => {
    seed({ mappings: [{ exportedName: 'user_sub', expression: 'a' }], eventTypes: ['USER_LOGIN'] });

    expect(component.rows).toHaveLength(1);
    expect(component.rows[0]).toMatchObject({ exportedName: 'user_sub', expression: 'a' });
    expect(component.eventTypes).toEqual(['USER_LOGIN']);
  });

  it('treats missing seed values as nothing configured', () => {
    seed({ mappings: null, eventTypes: null });

    expect(component.rows).toEqual([]);
    expect(component.eventTypes).toEqual([]);
  });

  it('does not emit when seeded', () => {
    seed({ mappings: [{ exportedName: 'user_sub', expression: 'a' }], eventTypes: [] });

    expect(emitted).toEqual([]);
  });

  it('discards local edits when reseeded', () => {
    seed({ mappings: [{ exportedName: 'user_sub', expression: 'a' }], eventTypes: [] });
    component.addMapping();

    seed({ mappings: [{ exportedName: 'user_sub', expression: 'a' }], eventTypes: [] });

    expect(component.rows).toHaveLength(1);
  });

  it('appends an incomplete row on add, and reports it as invalid', () => {
    seed({ mappings: [], eventTypes: [] });

    component.addMapping();

    expect(component.rows).toHaveLength(1);
    expect(emitted).toHaveLength(1);
    expect(emitted[0].isValid).toBe(false);
  });

  it('reports a completed row as valid, without the row bookkeeping', () => {
    seed({ mappings: [], eventTypes: [] });
    component.addMapping();

    component.rows[0].exportedName = 'user_sub';
    component.rows[0].expression = "{#context.attributes['user'].id}";
    component.onRowInput();

    const last = emitted[emitted.length - 1];
    expect(last.isValid).toBe(true);
    expect(last.mappings).toEqual([{ exportedName: 'user_sub', expression: "{#context.attributes['user'].id}" }]);
  });

  it('removes the row at the given index', () => {
    seed({
      mappings: [
        { exportedName: 'first', expression: 'a' },
        { exportedName: 'second', expression: 'b' },
        { exportedName: 'third', expression: 'c' },
      ],
      eventTypes: [],
    });

    component.removeMapping(1);

    expect(component.rows.map((row) => row.exportedName)).toEqual(['first', 'third']);
  });

  it('keeps the surviving rows themselves, so their controls are not rebuilt', () => {
    seed({
      mappings: [
        { exportedName: 'first', expression: 'a' },
        { exportedName: 'second', expression: 'b' },
      ],
      eventTypes: [],
    });
    const survivor = component.rows[1];

    component.removeMapping(0);

    expect(component.rows[0]).toBe(survivor);
  });

  it('reports event types chosen without a mapping as invalid', () => {
    seed({ mappings: [], eventTypes: [] });

    component.onEventTypesChange(['USER_LOGIN']);

    expect(emitted[emitted.length - 1].isValid).toBe(false);
  });

  it('stops offering to add once the maximum is reached', () => {
    seed({
      mappings: Array.from({ length: MAX_ATTRIBUTE_MAPPINGS }, (_, i) => ({ exportedName: `field${i}`, expression: 'a' })),
      eventTypes: [],
    });

    expect(component.canAdd).toBe(false);
  });
});
