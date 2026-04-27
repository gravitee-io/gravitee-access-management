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

@Component({
  selector: 'app-domain-trust-domains',
  templateUrl: './trust-domains.component.html',
  styleUrls: ['./trust-domains.component.scss'],
  standalone: false,
})
export class DomainSettingsTrustDomainsComponent implements OnInit {
  trustDomains: any[];
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

  bundleSourceLabel(source: string): string {
    return source === 'JWKS_URL' ? 'JWKS URL' : source;
  }

  delete(id: string, name: string, event: Event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Trust Domain', `Are you sure you want to delete "${name}"?`)
      .pipe(
        filter((res) => res),
        switchMap(() => this.trustDomainService.delete(this.domainId, id)),
        switchMap(() => this.trustDomainService.list(this.domainId)),
        tap((updated) => {
          this.trustDomains = updated;
          this.snackbarService.open('Trust domain deleted');
        }),
      )
      .subscribe({
        error: () => this.snackbarService.open('Failed to delete trust domain'),
      });
  }
}
