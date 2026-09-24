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
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NEVER, throwError } from 'rxjs';

import { DomainService } from '../../services/domain.service';
import { SnackbarService } from '../../services/snackbar.service';

import { DomainCreationComponent } from './domain-creation.component';

describe('DomainCreationComponent', () => {
  let component: DomainCreationComponent;
  let fixture: ComponentFixture<DomainCreationComponent>;
  let createButton: any;

  const mockDomainService = {
    create: jest.fn(),
  };

  const mockSnackbarService = {
    open: jest.fn(),
    openFromComponent: jest.fn(),
  };

  const mockRouter = {
    url: '/environments/default/domains/new',
    navigate: jest.fn(),
  };

  const mockActivatedRoute = {
    snapshot: {
      data: {
        dataPlanes: [{ id: 'default', name: 'Default' }],
      },
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [DomainCreationComponent],
      imports: [FormsModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: DomainService, useValue: mockDomainService },
        { provide: SnackbarService, useValue: mockSnackbarService },
        { provide: Router, useValue: mockRouter },
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
      ],
    }).compileComponents();
  });

  beforeEach(async () => {
    fixture = TestBed.createComponent(DomainCreationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();

    const nameInput = fixture.nativeElement.querySelector('input[name="name"]');
    nameInput.value = 'my-domain';
    nameInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    createButton = fixture.nativeElement.querySelector('gv-button[type="submit"]');
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should keep the create button disabled while the creation is pending', () => {
    mockDomainService.create.mockReturnValue(NEVER);

    component.create();
    fixture.detectChanges();

    expect(createButton.disabled).toBe(true);
  });

  it('should enable the create button again when the creation fails', () => {
    expect(createButton.disabled).toBe(false);
    mockDomainService.create.mockReturnValue(throwError(() => ({ error: { message: 'Domain already exists' } })));

    component.create();
    fixture.detectChanges();

    expect(mockSnackbarService.openFromComponent).toHaveBeenCalledWith('Errors', ['Domain already exists']);
    expect(createButton.disabled).toBe(false);
  });
});
