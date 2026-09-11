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
import { CommonModule } from '@angular/common';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';

import { SnackbarService } from '../../../../services/snackbar.service';

import { TokensComponent } from './tokens.component';

describe('TokensComponent', () => {
  let component: TokensComponent;
  let fixture: ComponentFixture<TokensComponent>;

  function createFixture(oauthSettings: Record<string, unknown>, context: 'Application' | 'McpServer' = 'Application'): void {
    fixture = TestBed.createComponent(TokensComponent);
    component = fixture.componentInstance;
    component.context = context;
    component.oauthSettings = oauthSettings;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CommonModule, FormsModule],
      declarations: [TokensComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
      providers: [
        { provide: MatDialog, useValue: { open: jest.fn() } },
        { provide: SnackbarService, useValue: { open: jest.fn() } },
      ],
    }).compileComponents();
  });

  it('shouldOfferIdJagWhenCrossAppAccessDisabled', () => {
    createFixture({});
    expect(component.claimTokenTypes).toEqual(['id_token', 'access_token', 'id_jag']);
  });

  it('shouldOfferIdJagWhenCrossAppAccessEnabled', () => {
    createFixture({ crossAppAccessSettings: { enabled: true } });
    expect(component.claimTokenTypes).toEqual(['id_token', 'access_token', 'id_jag']);
  });

  it('shouldOfferAccessTokenOnlyForMcpServer', () => {
    createFixture({ crossAppAccessSettings: { enabled: true } }, 'McpServer');
    expect(component.claimTokenTypes).toEqual(['access_token']);
  });

  it('shouldKeepClaimTokenTypesReferenceStableBetweenReads', () => {
    createFixture({});
    expect(component.claimTokenTypes).toBe(component.claimTokenTypes);
  });

  it('shouldRenderAllFourValidityFieldsForAnApplication', () => {
    createFixture({ idJagValiditySeconds: 300 });

    const names = Array.from(fixture.nativeElement.querySelectorAll('input[type="number"]')).map((input: any) => input.name);

    expect(names).toEqual(['accessTokenValidity', 'refreshTokenValidity', 'idTokenValidity', 'idJagValidity']);
  });

  it('shouldTitleTheValiditySectionTokensTtl', () => {
    createFixture({});

    expect(fixture.nativeElement.querySelector('h5').textContent.trim()).toBe('Tokens TTL (seconds)');
  });

  it('shouldShowIdJagValidityWhenCrossAppAccessDisabled', () => {
    createFixture({ idJagValiditySeconds: 300 });

    expect(fixture.nativeElement.querySelector('input[name="idJagValidity"]')).toBeTruthy();
  });

  it('shouldShowIdJagValidityWhenCrossAppAccessEnabled', () => {
    createFixture({ crossAppAccessSettings: { enabled: true }, idJagValiditySeconds: 300 });

    expect(fixture.nativeElement.querySelector('input[name="idJagValidity"]')).toBeTruthy();
  });

  it('shouldHideIdJagValidityForMcpServer', () => {
    createFixture({ idJagValiditySeconds: 300 }, 'McpServer');

    expect(fixture.nativeElement.querySelector('input[name="idJagValidity"]')).toBeNull();
  });

  it('shouldEmitIdJagValidityOnChange', () => {
    createFixture({ crossAppAccessSettings: { enabled: true }, idJagValiditySeconds: 120 });
    const emitted = jest.fn();
    component.settingsChange.subscribe(emitted);
    component.modelChanged();
    expect(emitted).toHaveBeenCalledWith(expect.objectContaining({ idJagValiditySeconds: 120 }));
  });
});
