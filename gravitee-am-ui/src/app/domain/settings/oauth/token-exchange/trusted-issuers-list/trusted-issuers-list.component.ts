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
import { Component, OnDestroy, OnInit } from '@angular/core';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';
import { Subject, takeUntil } from 'rxjs';
import { filter, switchMap, tap } from 'rxjs/operators';

import { AuthService } from '../../../../../services/auth.service';
import { DialogService } from '../../../../../services/dialog.service';
import { DomainService } from '../../../../../services/domain.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { DomainStoreService } from '../../../../../stores/domain.store';
import { TrustedIssuer } from '../token-exchange.types';

@Component({
  selector: 'app-trusted-issuers-list',
  templateUrl: './trusted-issuers-list.component.html',
  styleUrls: ['./trusted-issuers-list.component.scss'],
  standalone: false,
})
export class TrustedIssuersListComponent implements OnInit, OnDestroy {
  trustedIssuers: TrustedIssuer[] = [];
  domain: any = {};
  domainId: string;
  editMode: boolean;
  private destroy$ = new Subject<void>();

  constructor(
    private domainService: DomainService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainStore.domain$.pipe(takeUntil(this.destroy$)).subscribe((domain) => {
      this.domain = deepClone(domain);
      this.trustedIssuers = this.domain.tokenExchangeSettings?.trustedIssuers ?? [];
    });
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_openid_update']);
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get isEmpty(): boolean {
    return !this.trustedIssuers || this.trustedIssuers.length === 0;
  }

  getKeyMethodLabel(issuer: TrustedIssuer): string {
    return issuer.keyResolutionMethod === 'jwks_url' ? 'JWKS URL' : 'PEM Certificate';
  }

  getScopeMappingCount(issuer: TrustedIssuer): number {
    if (issuer.scopeMappings) {
      return Object.keys(issuer.scopeMappings).length;
    }
    return 0;
  }

  delete(index: number, event: Event) {
    event.preventDefault();
    const issuer = this.trustedIssuers[index];
    const issuerLabel = (issuer.issuer ?? '').trim() || `Issuer #${index + 1}`;
    this.dialogService
      .confirm('Delete Trusted Issuer', `Are you sure you want to delete "${issuerLabel}"?`)
      .pipe(
        filter((res) => res),
        switchMap(() => {
          const updatedIssuers = [...this.trustedIssuers];
          updatedIssuers.splice(index, 1);
          return this.domainService.patchTokenExchangeSettings(this.domainId, {
            ...this.domain,
            tokenExchangeSettings: {
              ...this.domain.tokenExchangeSettings,
              trustedIssuers: updatedIssuers,
            },
          });
        }),
        tap((data) => {
          this.domainStore.set(data);
          this.domain = deepClone(data);
          this.trustedIssuers = this.domain.tokenExchangeSettings?.trustedIssuers ?? [];
          this.snackbarService.open('Trusted issuer deleted');
        }),
      )
      .subscribe({
        error: () => this.snackbarService.open('Failed to delete trusted issuer'),
      });
  }
}
