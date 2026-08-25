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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { filter, switchMap, tap } from 'rxjs/operators';

import { DialogService } from '../../../services/dialog.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { TrustDomainService } from '../../../services/trust-domain.service';

import { keyMaterialSourceLabel, TrustDomain, trustDomainUsagesLabel } from './trust-domain.types';

@Component({
  selector: 'app-domain-trust-domains',
  templateUrl: './trust-domains.component.html',
  styleUrls: ['./trust-domains.component.scss'],
  standalone: false,
})
export class DomainSettingsTrustDomainsComponent implements OnInit {
  trustDomains: TrustDomain[];
  domainId: string;

  constructor(
    private route: ActivatedRoute,
    private trustDomainService: TrustDomainService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.trustDomains = this.route.snapshot.data['trustDomains'] ?? [];
  }

  isEmpty(): boolean {
    return !this.trustDomains || this.trustDomains.length === 0;
  }

  usagesLabel(trustDomain: TrustDomain): string {
    return trustDomainUsagesLabel(trustDomain);
  }

  subtitle(trustDomain: TrustDomain): string {
    if (trustDomain.description) {
      return trustDomain.description;
    }
    return (
      trustDomain.issuer ??
      trustDomain.spiffeTrustDomain ??
      trustDomain.keyMaterial?.jwksUrl ??
      keyMaterialSourceLabel(trustDomain.keyMaterial?.source)
    );
  }

  keySourceLabel(trustDomain: TrustDomain): string {
    return keyMaterialSourceLabel(trustDomain.keyMaterial?.source);
  }

  delete(id: string, name: string, event: Event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Trusted Domain', `Are you sure you want to delete "${name}"?`)
      .pipe(
        filter((res) => res),
        switchMap(() => this.trustDomainService.delete(this.domainId, id)),
        switchMap(() => this.trustDomainService.list(this.domainId)),
        tap((updated) => {
          this.trustDomains = updated;
          this.snackbarService.open('Trusted domain deleted');
        }),
      )
      .subscribe({
        error: () => this.snackbarService.open('Failed to delete trusted domain'),
      });
  }
}
