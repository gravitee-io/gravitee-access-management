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
import { ActivatedRoute } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';

import { CertificateService } from '../../../services/certificate.service';
import { DomainService } from '../../../services/domain.service';
import { DialogService } from '../../../services/dialog.service';
import { SnackbarService } from '../../../services/snackbar.service';

import { DomainSettingsCertificatesComponent } from './certificates.component';

describe('DomainSettingsCertificatesComponent', () => {
  let component: DomainSettingsCertificatesComponent;
  let fixture: ComponentFixture<DomainSettingsCertificatesComponent>;

  beforeEach(waitForAsync(() => {
    const certificateServiceStub = {
      findByDomain: jest.fn().mockReturnValue(of([])),
      delete: jest.fn().mockReturnValue(of(void 0)),
      rotateCertificate: jest.fn().mockReturnValue(of({})),
      publicKeys: jest.fn().mockReturnValue(of([])),
    } as Partial<CertificateService> as CertificateService;

    const domainServiceStub = {
      patch: jest.fn().mockReturnValue(of({})),
    } as Partial<DomainService> as DomainService;

    const dialogServiceStub = {
      confirm: jest.fn().mockReturnValue(of(true)),
    } as Partial<DialogService> as DialogService;

    const snackbarServiceStub = {
      open: jest.fn(),
    } as Partial<SnackbarService> as SnackbarService;

    const activatedRouteStub = {
      snapshot: {
        data: {
          domain: { id: 'test-domain', certificateSettings: { fallbackCertificate: null } },
          certificates: [],
        },
      },
    } as any as ActivatedRoute;

    const matDialogStub = {
      open: jest.fn().mockReturnValue({
        componentInstance: {},
        afterClosed: () => of({ action: 'cancel' }),
      }),
    } as Partial<MatDialog> as MatDialog;

    TestBed.configureTestingModule({
      declarations: [DomainSettingsCertificatesComponent],
      providers: [
        { provide: CertificateService, useValue: certificateServiceStub },
        { provide: DomainService, useValue: domainServiceStub },
        { provide: DialogService, useValue: dialogServiceStub },
        { provide: SnackbarService, useValue: snackbarServiceStub },
        { provide: ActivatedRoute, useValue: activatedRouteStub },
        { provide: MatDialog, useValue: matDialogStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DomainSettingsCertificatesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
