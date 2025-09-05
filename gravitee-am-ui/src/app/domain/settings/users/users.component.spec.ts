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
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';

import { UsersComponent } from './users.component';
import { UserService } from '../../../services/user.service';
import { OrganizationService } from '../../../services/organization.service';
import { DialogService } from '../../../services/dialog.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { AuthService } from '../../../services/auth.service';
import { ApplicationService } from '../../../services/application.service';
import { ProviderService } from '../../../services/provider.service';

describe('UsersComponent', () => {
  let component: UsersComponent;
  let fixture: ComponentFixture<UsersComponent>;

  beforeEach(waitForAsync(() => {
    const userServiceStub = {
      findByDomain: jest.fn().mockReturnValue(of({ totalCount: 0, data: [] })),
      search: jest.fn().mockReturnValue(of({ totalCount: 0, data: [] })),
      delete: jest.fn().mockReturnValue(of(void 0)),
    } as Partial<UserService> as UserService;

    const organizationServiceStub = {
      users: jest.fn().mockReturnValue(of({ totalCount: 0, data: [] })),
    } as Partial<OrganizationService> as OrganizationService;

    const dialogServiceStub = {
      confirm: jest.fn().mockReturnValue(of(true)),
    } as Partial<DialogService> as DialogService;

    const snackbarServiceStub = {
      open: jest.fn(),
    } as Partial<SnackbarService> as SnackbarService;

    const authServiceStub = {
      hasPermissions: jest.fn().mockReturnValue(false),
    } as Partial<AuthService> as AuthService;

    const applicationServiceStub = {
      findByDomain: jest.fn().mockReturnValue(of({ data: [] })),
    } as Partial<ApplicationService> as ApplicationService;

    const providerServiceStub = {
      findByDomain: jest.fn().mockReturnValue(of([])),
    } as Partial<ProviderService> as ProviderService;

    const activatedRouteStub = {
      snapshot: { data: { domain: { id: 'test-domain' } } },
    } as any as ActivatedRoute;

    const routerStub = {
      routerState: { snapshot: { url: '/domains/test-domain/users' } },
    } as Partial<Router> as Router;

    const matDialogStub = {
      open: jest.fn(),
    } as Partial<MatDialog> as MatDialog;

    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      declarations: [UsersComponent],
      providers: [
        { provide: UserService, useValue: userServiceStub },
        { provide: OrganizationService, useValue: organizationServiceStub },
        { provide: DialogService, useValue: dialogServiceStub },
        { provide: SnackbarService, useValue: snackbarServiceStub },
        { provide: AuthService, useValue: authServiceStub },
        { provide: ApplicationService, useValue: applicationServiceStub },
        { provide: ProviderService, useValue: providerServiceStub },
        { provide: ActivatedRoute, useValue: activatedRouteStub },
        { provide: Router, useValue: routerStub },
        { provide: MatDialog, useValue: matDialogStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(UsersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  describe('isAdvancedSearch', () => {
    it('should return true if the search query is an advanced search', () => {
      const searchQueries = [
        'username eq "bob"',
        'email co "@someplace.co.uk"',
        'title pr',
        'age gt 21',
        'meta.lastModified ge "2020-01-01T00:00:00Z"',
        'userType sw "Admin"',
        'nickName ew "y"',
        'name.familyName ne "Smith"',
        'username eq "bob" and email co "@somewhere.com"',
        '(username eq "bob") or (username eq "alice")',
        '"tricky and string" or title pr',
        'nickName ew ""',
        'name.familyName eq   "O\'Connor"',
      ];
      expect(searchQueries.map(query => component['isAdvancedSearch'](query))).not.toContain(false);
    });

    it('should return false if the search query is not an advanced search', () => {
      const searchQueries = [
        '@someplace.co.uk',
        'just some random text',
        '"this string contains co inside"',
        '"escaped \\"eq\\" inside quotes"',
        'foo.eq.bar',
        'anderson',
        'coral reef',
        'not_equal',
        'gtx1080',
        '"foo \\"co\\" bar"',
        'co',
        '"eq"',
        'sw "start"and',
      ];
      expect(searchQueries.map(query => component['isAdvancedSearch'](query))).not.toContain(true);
    });
  });
});
