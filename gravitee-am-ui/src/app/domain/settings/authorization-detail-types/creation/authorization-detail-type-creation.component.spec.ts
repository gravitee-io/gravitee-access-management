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
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthorizationDetailTypeService } from '../../../../services/authorization-detail-type.service';
import { SnackbarService } from '../../../../services/snackbar.service';

import { AuthorizationDetailTypeCreationComponent } from './authorization-detail-type-creation.component';

describe('AuthorizationDetailTypeCreationComponent', () => {
  const route = { snapshot: { data: { domain: { id: 'domain-id' } } } };
  let component: AuthorizationDetailTypeCreationComponent;
  let fixture: ComponentFixture<AuthorizationDetailTypeCreationComponent>;
  let service: { create: jest.Mock };
  let router: { navigate: jest.Mock };
  let snackbarService: { open: jest.Mock };
  let dialog: { open: jest.Mock };

  beforeEach(async () => {
    service = { create: jest.fn().mockReturnValue(of({ id: 'new-id', type: 'payment' })) };
    router = { navigate: jest.fn() };
    snackbarService = { open: jest.fn() };
    dialog = { open: jest.fn() };

    TestBed.configureTestingModule({
      imports: [FormsModule],
      declarations: [AuthorizationDetailTypeCreationComponent],
      providers: [
        { provide: AuthorizationDetailTypeService, useValue: service },
        { provide: Router, useValue: router },
        { provide: SnackbarService, useValue: snackbarService },
        { provide: MatDialog, useValue: dialog },
        { provide: ActivatedRoute, useValue: route },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    }).compileComponents();

    fixture = TestBed.createComponent(AuthorizationDetailTypeCreationComponent);
    component = fixture.componentInstance;
    component.ngOnInit();
  });

  afterEach(() => fixture?.destroy());

  it('should send the form and open the new type', () => {
    component.authorizationDetailType = { type: 'payment', name: 'Payment', description: 'd', schema: '{}', discovery: true };
    component.expiresIn = 2;
    component.unitTime = 'minutes';

    component.create();

    expect(service.create).toHaveBeenCalledWith('domain-id', {
      type: 'payment',
      name: 'Payment',
      description: 'd',
      schema: '{}',
      discovery: true,
      expiresIn: 120,
    });
    expect(snackbarService.open).toHaveBeenCalledWith('Authorization detail type payment created');
    expect(router.navigate).toHaveBeenCalledWith(['..', 'new-id'], { relativeTo: route });
  });

  it('should leave out the optional fields left empty', () => {
    component.authorizationDetailType = { type: 'payment', name: 'Payment', description: '', schema: '  ', discovery: false };

    component.create();

    expect(service.create).toHaveBeenCalledWith('domain-id', {
      type: 'payment',
      name: 'Payment',
      discovery: false,
      description: undefined,
      schema: undefined,
      expiresIn: undefined,
    });
  });

  it('should stay on the form when the server refuses', () => {
    service.create.mockReturnValue(throwError(() => new Error('already exists')));
    component.authorizationDetailType = { type: 'payment', name: 'Payment', discovery: false };

    component.create();

    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should show why the server refused until the form changes', () => {
    service.create.mockReturnValue(throwError(() => ({ status: 400, error: { message: 'Schema root must declare a type' } })));
    component.authorizationDetailType = { type: 'payment', name: 'Payment', discovery: false, schema: '{}' };

    component.create();
    expect(component.saveError).toBe('Schema root must declare a type');

    component.authorizationDetailType.schema = '{ }';
    expect(component.saveError).toBeNull();

    component.authorizationDetailType.schema = '{}';
    expect(component.saveError).toBe('Schema root must declare a type');
  });

  it('should fill the editor with the schema example the dialog returns', () => {
    dialog.open.mockReturnValue({ afterClosed: () => of('{"type":"object"}') });
    component.authorizationDetailType.type = 'payment';

    component.openSchemaExample();

    expect(dialog.open.mock.calls[0][1].data).toEqual({ type: 'payment', hasSchema: false, editable: true });
    expect(component.authorizationDetailType.schema).toBe('{"type":"object"}');
  });

  it('should leave the editor alone when the schema example is closed', () => {
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });
    component.authorizationDetailType.schema = '{}';

    component.openSchemaExample();

    expect(dialog.open.mock.calls[0][1].data.hasSchema).toBe(true);
    expect(component.authorizationDetailType.schema).toBe('{}');
  });

  it('should format the schema', () => {
    component.authorizationDetailType.schema = '{"type":"object"}';

    component.formatSchema();

    expect(component.authorizationDetailType.schema).toBe('{\n  "type": "object"\n}');
  });

  it('should report an invalid schema and expiry', () => {
    component.authorizationDetailType.schema = '{';
    component.expiresIn = 1;

    expect(component.schemaError).toEqual('Schema must be valid JSON');
    expect(component.expiryError).toEqual('Select a unit for the duration');
  });
});
