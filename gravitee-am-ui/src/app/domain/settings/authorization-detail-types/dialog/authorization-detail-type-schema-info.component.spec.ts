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
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { AuthorizationDetailTypeSchemaInfoDialogComponent, SchemaInfoDialogData } from './authorization-detail-type-schema-info.component';

describe('AuthorizationDetailTypeSchemaInfoDialogComponent', () => {
  let fixture: ComponentFixture<AuthorizationDetailTypeSchemaInfoDialogComponent>;

  const create = (data: SchemaInfoDialogData) => {
    TestBed.configureTestingModule({
      declarations: [AuthorizationDetailTypeSchemaInfoDialogComponent],
      providers: [
        { provide: MatDialogRef, useValue: { close: jest.fn() } },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
    fixture = TestBed.createComponent(AuthorizationDetailTypeSchemaInfoDialogComponent);
    return fixture.componentInstance;
  };

  afterEach(() => fixture?.destroy());

  it('should build the example from the type', () => {
    const component = create({ type: 'payment', hasSchema: false, editable: true });

    expect(JSON.parse(component.example).properties.type.const).toBe('payment');
    expect(component.unusableReason).toBeNull();
  });

  it('should show a placeholder and refuse to be used without a type', () => {
    const component = create({ hasSchema: false, editable: true });

    expect(JSON.parse(component.example).properties.type.const).toBe('your_type');
    expect(component.unusableReason).toBe('Enter a type to use this example.');
  });

  it('should refuse to replace a schema', () => {
    const component = create({ type: 'payment', hasSchema: true, editable: true });

    expect(component.unusableReason).toBe('Clear the schema to use this example.');
  });
});
