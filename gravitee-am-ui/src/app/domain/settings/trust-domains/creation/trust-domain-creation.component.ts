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

import { SnackbarService } from '../../../../services/snackbar.service';
import { TrustDomainService } from '../../../../services/trust-domain.service';

@Component({
  selector: 'app-trust-domain-creation',
  templateUrl: './trust-domain-creation.component.html',
  styleUrls: ['./trust-domain-creation.component.scss'],
  standalone: false,
})
export class TrustDomainCreationComponent implements OnInit {
  domainId: string;

  trustDomain = {
    name: '',
    description: '',
    bundleSource: 'JWKS_URL',
    jwksUrl: '',
    refreshIntervalSeconds: 300,
    allowedAlgorithms: [] as string[],
  };

  algorithmInput = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private trustDomainService: TrustDomainService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.parent.parent.data['domain'].id;
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

  create(): void {
    const payload = {
      ...this.trustDomain,
      allowedAlgorithms: this.trustDomain.allowedAlgorithms.length ? this.trustDomain.allowedAlgorithms : null,
    };
    this.trustDomainService.create(this.domainId, payload).subscribe({
      next: () => {
        this.snackbarService.open('Trust domain created');
        this.router.navigate(['..'], { relativeTo: this.route });
      },
      error: (err: unknown) => {
        const message = (err as any)?.error?.message || 'Failed to create trust domain';
        this.snackbarService.open(message);
      },
    });
  }
}
