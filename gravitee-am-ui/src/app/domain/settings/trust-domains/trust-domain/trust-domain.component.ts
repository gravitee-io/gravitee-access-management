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
import { ActivatedRoute, Router } from '@angular/router';
import { filter, switchMap, tap } from 'rxjs/operators';

import { AuthService } from '../../../../services/auth.service';
import { DialogService } from '../../../../services/dialog.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { TrustDomainService } from '../../../../services/trust-domain.service';
import { TrustDomain, trustDomainKindLabel } from '../trust-domain.types';

@Component({
  selector: 'app-trust-domain',
  templateUrl: './trust-domain.component.html',
  styleUrls: ['./trust-domain.component.scss'],
  standalone: false,
})
export class TrustDomainComponent implements OnInit {
  trustDomain: TrustDomain;
  domainId: string;
  editMode: boolean;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private trustDomainService: TrustDomainService,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private authService: AuthService,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.parent.parent.data['domain'].id;
    this.trustDomain = this.route.snapshot.data['trustDomain'];
    this.editMode = this.authService.hasPermissions(['domain_trust_domain_update']);
  }

  get kindLabel(): string {
    return trustDomainKindLabel(this.trustDomain?.kind);
  }

  save(payload: TrustDomain): void {
    this.trustDomainService.update(this.domainId, this.trustDomain.id, payload).subscribe({
      next: (updated) => {
        this.trustDomain = updated;
        this.snackbarService.open('Trusted domain updated');
      },
      error: (err: unknown) => {
        const message = (err as any)?.error?.message || 'Failed to update trusted domain';
        this.snackbarService.open(message);
      },
    });
  }

  delete(event: Event): void {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Trusted Domain', `Are you sure you want to delete "${this.trustDomain.name}"?`)
      .pipe(
        filter((res) => res),
        switchMap(() => this.trustDomainService.delete(this.domainId, this.trustDomain.id)),
        tap(() => {
          this.snackbarService.open('Trusted domain deleted');
          this.router.navigate(['..'], { relativeTo: this.route });
        }),
      )
      .subscribe({
        error: () => this.snackbarService.open('Failed to delete trusted domain'),
      });
  }
}
