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

@Component({
  selector: 'app-trust-domain',
  templateUrl: './trust-domain.component.html',
  styleUrls: ['./trust-domain.component.scss'],
  standalone: false,
})
export class TrustDomainComponent implements OnInit {
  trustDomain: any;
  domainId: string;
  editMode: boolean;
  algorithmInput = '';

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
    this.trustDomain = this.normalize(this.route.snapshot.data['trustDomain']);
    this.editMode = this.authService.hasPermissions(['domain_trust_domain_update']);
  }

  private normalize(td: any): any {
    return {
      ...td,
      allowedAlgorithms: td?.allowedAlgorithms ?? [],
    };
  }

  addAlgorithm(value: string): void {
    const trimmed = (value ?? '').trim();
    if (trimmed && !this.trustDomain.allowedAlgorithms.includes(trimmed)) {
      this.trustDomain.allowedAlgorithms.push(trimmed);
    }
    this.algorithmInput = '';
  }

  removeAlgorithm(alg: string): void {
    this.trustDomain.allowedAlgorithms = this.trustDomain.allowedAlgorithms.filter((a) => a !== alg);
  }

  save(): void {
    const payload = {
      ...this.trustDomain,
      allowedAlgorithms: this.trustDomain.allowedAlgorithms.length ? this.trustDomain.allowedAlgorithms : null,
    };
    this.trustDomainService.update(this.domainId, this.trustDomain.id, payload).subscribe({
      next: (updated) => {
        this.trustDomain = this.normalize(updated);
        this.snackbarService.open('Trust domain updated');
      },
      error: (err: unknown) => {
        const message = (err as any)?.error?.message || 'Failed to update trust domain';
        this.snackbarService.open(message);
      },
    });
  }

  delete(event: Event): void {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Trust Domain', `Are you sure you want to delete "${this.trustDomain.name}"?`)
      .pipe(
        filter((res) => res),
        switchMap(() => this.trustDomainService.delete(this.domainId, this.trustDomain.id)),
        tap(() => {
          this.snackbarService.open('Trust domain deleted');
          this.router.navigate(['..'], { relativeTo: this.route });
        }),
      )
      .subscribe({
        error: () => this.snackbarService.open('Failed to delete trust domain'),
      });
  }
}
