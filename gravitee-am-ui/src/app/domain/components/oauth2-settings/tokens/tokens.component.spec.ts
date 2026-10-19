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
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatTooltip, MatTooltipModule } from '@angular/material/tooltip';
import { By } from '@angular/platform-browser';

import { SnackbarService } from '../../../../services/snackbar.service';
import { LightweightJwtInfoDialogComponent } from '../dialog/lightweight-jwt-info.component';

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
      imports: [CommonModule, FormsModule, MatChipsModule, MatTooltipModule],
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

  it('shouldOfferAccessTokenAndIdJagForMcpServer', () => {
    createFixture({ crossAppAccessSettings: { enabled: true } }, 'McpServer');
    expect(component.claimTokenTypes).toEqual(['access_token', 'id_jag']);
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

  it('shouldRenderAccessTokenAndIdJagValidityFieldsForMcpServer', () => {
    createFixture({ idJagValiditySeconds: 300 }, 'McpServer');

    const names = Array.from(fixture.nativeElement.querySelectorAll('input[type="number"]')).map((input: any) => input.name);

    expect(names).toEqual(['accessTokenValidity', 'idJagValidity']);
  });

  it('shouldEmitIdJagValidityOnChange', () => {
    createFixture({ crossAppAccessSettings: { enabled: true }, idJagValiditySeconds: 120 });
    const emitted = jest.fn();
    component.settingsChange.subscribe(emitted);
    component.modelChanged();
    expect(emitted).toHaveBeenCalledWith(expect.objectContaining({ idJagValiditySeconds: 120 }));
  });
  it('shouldShowLightweightJwtToggleForApplication', () => {
    createFixture({});

    expect(fixture.nativeElement.querySelector('[data-testid="lightweight-jwt-toggle"]')).toBeTruthy();
  });

  it('shouldHideLightweightJwtToggleForMcpServer', () => {
    createFixture({}, 'McpServer');

    expect(fixture.nativeElement.querySelector('[data-testid="lightweight-jwt-toggle"]')).toBeNull();
  });

  it('shouldReportLightweightJwtDisabledByDefault', () => {
    createFixture({});

    expect(component.isLightweightJwtEnabled()).toBe(false);
  });

  it('shouldEmitLightweightJwtSettingsWhenToggled', () => {
    createFixture({});
    const emitted = jest.fn();
    component.settingsChange.subscribe(emitted);

    component.toggleLightweightJwt({ checked: true });

    expect(emitted).toHaveBeenCalledWith(expect.objectContaining({ lightweightJwtSettings: { enabled: true } }));
  });

  it('shouldHideAllowlistsWhenLightweightJwtDisabled', () => {
    createFixture({ lightweightJwtSettings: { enabled: false, allowedAccessTokenClaims: ['tenant'] } });

    expect(fixture.nativeElement.querySelector('[data-testid="lightweight-jwt-allowlists"]')).toBeNull();
  });

  it('shouldShowAllowlistsWhenLightweightJwtEnabled', () => {
    createFixture({ lightweightJwtSettings: { enabled: true } });

    expect(fixture.nativeElement.querySelector('[data-testid="allowedAccessTokenClaims-input"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="allowedIdTokenClaims-input"]')).toBeTruthy();
  });

  it('shouldAddTrimmedClaimToAllowlist', () => {
    createFixture({ lightweightJwtSettings: { enabled: true } });
    const emitted = jest.fn();
    component.settingsChange.subscribe(emitted);
    const clear = jest.fn();

    component.addLightweightJwtAllowlistClaim('allowedAccessTokenClaims', { value: ' tenant ', chipInput: { clear } } as any);

    expect(clear).toHaveBeenCalled();
    expect(emitted).toHaveBeenCalledWith(
      expect.objectContaining({ lightweightJwtSettings: { enabled: true, allowedAccessTokenClaims: ['tenant'] } }),
    );
  });

  it('shouldKeepAllowlistsIndependent', () => {
    createFixture({ lightweightJwtSettings: { enabled: true, allowedAccessTokenClaims: ['tenant'] } });

    component.addLightweightJwtAllowlistClaim('allowedIdTokenClaims', { value: 'email', chipInput: { clear: jest.fn() } } as any);

    expect(component.lightweightJwtAllowlist('allowedAccessTokenClaims')).toEqual(['tenant']);
    expect(component.lightweightJwtAllowlist('allowedIdTokenClaims')).toEqual(['email']);
  });

  it('shouldIgnoreBlankClaims', () => {
    createFixture({ lightweightJwtSettings: { enabled: true } });
    const emitted = jest.fn();
    component.settingsChange.subscribe(emitted);

    component.addLightweightJwtAllowlistClaim('allowedAccessTokenClaims', { value: '  ', chipInput: { clear: jest.fn() } } as any);

    expect(emitted).not.toHaveBeenCalled();
    expect(TestBed.inject(SnackbarService).open).not.toHaveBeenCalled();
  });

  it('shouldRejectDuplicateClaim', () => {
    createFixture({ lightweightJwtSettings: { enabled: true, allowedAccessTokenClaims: ['tenant'] } });
    const emitted = jest.fn();
    component.settingsChange.subscribe(emitted);

    component.addLightweightJwtAllowlistClaim('allowedAccessTokenClaims', { value: 'tenant', chipInput: { clear: jest.fn() } } as any);

    expect(emitted).not.toHaveBeenCalled();
    expect(TestBed.inject(SnackbarService).open).toHaveBeenCalledWith('Claim already exists');
    expect(component.lightweightJwtAllowlist('allowedAccessTokenClaims')).toEqual(['tenant']);
  });

  it('shouldRejectReservedClaimsCaseSensitively', () => {
    createFixture({ lightweightJwtSettings: { enabled: true } });

    component.addLightweightJwtAllowlistClaim('allowedAccessTokenClaims', {
      value: 'orig_resources',
      chipInput: { clear: jest.fn() },
    } as any);
    component.addLightweightJwtAllowlistClaim('allowedIdTokenClaims', { value: 'nonce', chipInput: { clear: jest.fn() } } as any);
    component.addLightweightJwtAllowlistClaim('allowedIdTokenClaims', { value: 'ISS', chipInput: { clear: jest.fn() } } as any);

    const snackbar = TestBed.inject(SnackbarService).open;
    expect(snackbar).toHaveBeenCalledWith('orig_resources is a reserved claim and is always kept');
    expect(snackbar).toHaveBeenCalledWith('nonce is a reserved claim and is always kept');
    expect(snackbar).toHaveBeenCalledTimes(2);
    expect(component.lightweightJwtAllowlist('allowedAccessTokenClaims')).toEqual([]);
    expect(component.lightweightJwtAllowlist('allowedIdTokenClaims')).toEqual(['ISS']);
  });

  it('shouldCollapseReservedClaimsByDefaultAndToggleOnClick', () => {
    createFixture({ lightweightJwtSettings: { enabled: true } });
    const toggle = () => fixture.debugElement.query(By.css('[data-testid="allowedAccessTokenClaims-reserved-toggle"]'));
    const reservedCount = () => fixture.nativeElement.querySelectorAll('[data-testid="allowedAccessTokenClaims-reserved"]').length;

    expect(reservedCount()).toBe(0);
    expect(toggle().nativeElement.textContent).toContain('...');
    expect(toggle().injector.get(MatTooltip).message).toBe('Reserved claims: click to expand');

    toggle().nativeElement.click();
    fixture.detectChanges();

    expect(reservedCount()).toBe(component.lightweightJwtReservedClaims.allowedAccessTokenClaims.length);
    expect(toggle().injector.get(MatTooltip).message).toBe('Click to collapse reserved claims');
    expect(fixture.nativeElement.querySelectorAll('[data-testid="allowedIdTokenClaims-reserved"]')).toHaveLength(0);

    toggle().nativeElement.click();
    fixture.detectChanges();

    expect(reservedCount()).toBe(0);
  });

  it('shouldShowReservedClaimsExpandedWithoutToggleWhenReadonly', () => {
    fixture = TestBed.createComponent(TokensComponent);
    component = fixture.componentInstance;
    component.readonly = true;
    component.oauthSettings = { lightweightJwtSettings: { enabled: true } };
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="allowedAccessTokenClaims-reserved-toggle"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="allowedIdTokenClaims-reserved-toggle"]')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('[data-testid="allowedAccessTokenClaims-reserved"]')).toHaveLength(
      component.lightweightJwtReservedClaims.allowedAccessTokenClaims.length,
    );
    expect(fixture.nativeElement.querySelectorAll('[data-testid="allowedIdTokenClaims-reserved"]')).toHaveLength(
      component.lightweightJwtReservedClaims.allowedIdTokenClaims.length,
    );
  });

  it('shouldRenderReservedClaimsAsLockedChipsWithTooltips', () => {
    createFixture({ lightweightJwtSettings: { enabled: true, allowedAccessTokenClaims: ['tenant'] } });
    component.toggleLightweightJwtReservedClaims('allowedAccessTokenClaims');
    component.toggleLightweightJwtReservedClaims('allowedIdTokenClaims');
    fixture.detectChanges();

    const tooltips = (testId: string) =>
      fixture.debugElement
        .queryAll(By.css(`[data-testid="${testId}"]`))
        .map((chip) => [chip.nativeElement.textContent.trim().replace(/^lock\s*/, ''), chip.injector.get(MatTooltip).message]);

    const accessTokenChips = tooltips('allowedAccessTokenClaims-reserved');
    expect(accessTokenChips).toContainEqual(['iss', 'Reserved claim: Issuer']);
    expect(accessTokenChips).toContainEqual(['orig_resources', 'Reserved claim: Original resources']);
    expect(accessTokenChips).toContainEqual(['gis', 'Reserved claim: Gravitee internal subject (internal)']);
    expect(accessTokenChips.filter(([name]) => name === 'iss')).toHaveLength(1);
    expect(tooltips('allowedIdTokenClaims-reserved')).toContainEqual(['auth_time', 'Reserved claim: Authentication time']);
    expect(tooltips('allowedIdTokenClaims-reserved').map(([name]) => name)).not.toContain('jti');

    const reservedChips = fixture.nativeElement.querySelectorAll('.reserved-claim');
    reservedChips.forEach((chip: HTMLElement) => expect(chip.querySelector('[matChipRemove]')).toBeNull());
    expect(fixture.nativeElement.querySelectorAll('.reserved-claim-internal')).toHaveLength(4);
  });

  it('shouldRejectTooLongClaim', () => {
    createFixture({ lightweightJwtSettings: { enabled: true } });

    component.addLightweightJwtAllowlistClaim('allowedIdTokenClaims', { value: 'x'.repeat(129), chipInput: { clear: jest.fn() } } as any);

    expect(TestBed.inject(SnackbarService).open).toHaveBeenCalledWith('Claim name must be at most 128 characters');
    expect(component.lightweightJwtAllowlist('allowedIdTokenClaims')).toEqual([]);
  });

  it('shouldRemoveClaimFromAllowlist', () => {
    createFixture({ lightweightJwtSettings: { enabled: true, allowedIdTokenClaims: ['email', 'tenant'] } });

    component.removeLightweightJwtAllowlistClaim('allowedIdTokenClaims', 'email');

    expect(component.lightweightJwtAllowlist('allowedIdTokenClaims')).toEqual(['tenant']);
  });

  it('shouldKeepAllowlistsWhenToggledOffAndOn', () => {
    createFixture({ lightweightJwtSettings: { enabled: true, allowedAccessTokenClaims: ['tenant'], allowedIdTokenClaims: ['email'] } });

    component.toggleLightweightJwt({ checked: false });
    component.toggleLightweightJwt({ checked: true });

    expect(component.oauthSettings.lightweightJwtSettings).toEqual({
      enabled: true,
      allowedAccessTokenClaims: ['tenant'],
      allowedIdTokenClaims: ['email'],
    });
  });

  it('shouldListCustomClaimsRemovedByLightweightJwt', () => {
    createFixture({
      lightweightJwtSettings: { enabled: true, allowedAccessTokenClaims: ['tenant'], allowedIdTokenClaims: [] },
      tokenCustomClaims: [
        { tokenType: 'access_token', claimName: 'tenant', claimValue: 'a' },
        { tokenType: 'access_token', claimName: 'region', claimValue: 'b' },
        { tokenType: 'access_token', claimName: 'sub', claimValue: 'c' },
        { tokenType: 'id_token', claimName: 'tenant', claimValue: 'd' },
        { tokenType: 'id_token', claimName: 'nonce', claimValue: 'e' },
        { tokenType: 'id_jag', claimName: 'region', claimValue: 'f' },
      ],
    });

    expect(component.lightweightJwtRemovedClaims()).toEqual(['region (access_token)', 'tenant (id_token)']);
    expect(fixture.nativeElement.querySelector('[data-testid="lightweight-jwt-removed-claims"]').textContent).toContain(
      'region (access_token), tenant (id_token)',
    );
  });

  it('shouldNotListRemovedClaimsWhenLightweightJwtDisabled', () => {
    createFixture({
      lightweightJwtSettings: { enabled: false },
      tokenCustomClaims: [{ tokenType: 'access_token', claimName: 'region', claimValue: 'b' }],
    });

    expect(component.lightweightJwtRemovedClaims()).toEqual([]);
    expect(fixture.nativeElement.querySelector('[data-testid="lightweight-jwt-removed-claims"]')).toBeNull();
  });

  it('shouldOpenLightweightJwtClaimsDialogFromInfoButton', () => {
    createFixture({});

    fixture.nativeElement.querySelector('[data-testid="lightweight-jwt-info"]').click();

    expect(TestBed.inject(MatDialog).open).toHaveBeenCalledWith(LightweightJwtInfoDialogComponent, {});
  });
});
