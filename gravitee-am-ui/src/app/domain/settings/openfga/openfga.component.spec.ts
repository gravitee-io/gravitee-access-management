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
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';

import { OpenFGAComponent } from './openfga.component';
import { OpenFGAService } from '../../../services/openfga.service';
import { SnackbarService } from '../../../services/snackbar.service';

describe('OpenFGAComponent', () => {
  let component: OpenFGAComponent;
  let fixture: ComponentFixture<OpenFGAComponent>;

  beforeEach(async () => {
    const openFGAServiceSpy = jasmine.createSpyObj('OpenFGAService', [
      'getConfiguration',
      'updateConfiguration',
      'testConnection',
      'listStores',
      'createStore',
      'deleteStore'
    ]);

    const snackbarServiceSpy = jasmine.createSpyObj('SnackbarService', ['open']);

    const activatedRouteSpy = {
      snapshot: {
        params: {
          domainId: 'test-domain-id'
        }
      }
    };

    await TestBed.configureTestingModule({
      imports: [
        OpenFGAComponent,
        NoopAnimationsModule,
        HttpClientTestingModule,
      ],
      providers: [
        { provide: OpenFGAService, useValue: openFGAServiceSpy },
        { provide: SnackbarService, useValue: snackbarServiceSpy },
        { provide: ActivatedRoute, useValue: activatedRouteSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OpenFGAComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});