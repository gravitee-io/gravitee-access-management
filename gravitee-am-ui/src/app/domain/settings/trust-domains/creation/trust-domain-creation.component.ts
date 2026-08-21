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
import {
  DEFAULT_REFRESH_INTERVAL_SECONDS,
  TOKEN_EXCHANGE,
  TRUST_DOMAIN_KIND_OPTIONS,
  TrustDomain,
  TrustDomainKind,
} from '../trust-domain.types';

@Component({
  selector: 'app-trust-domain-creation',
  templateUrl: './trust-domain-creation.component.html',
  styleUrls: ['./trust-domain-creation.component.scss'],
  standalone: false,
})
export class TrustDomainCreationComponent implements OnInit {
  readonly TRUST_DOMAIN_KIND_OPTIONS = TRUST_DOMAIN_KIND_OPTIONS;

  domainId: string;
  kind: TrustDomainKind;
  trustDomain: TrustDomain;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private trustDomainService: TrustDomainService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.parent.parent.data['domain'].id;
    this.selectKind(TOKEN_EXCHANGE);
  }

  selectKind(kind: TrustDomainKind): void {
    this.kind = kind;
    this.trustDomain = {
      kind,
      name: '',
      refreshIntervalSeconds: DEFAULT_REFRESH_INTERVAL_SECONDS,
    };
  }

  create(payload: TrustDomain): void {
    this.trustDomainService.create(this.domainId, payload).subscribe({
      next: (created) => {
        this.snackbarService.open('Trusted domain created');
        this.router.navigate(['..', created.id], { relativeTo: this.route });
      },
      error: (err: unknown) => {
        const message = (err as any)?.error?.message || 'Failed to create trusted domain';
        this.snackbarService.open(message);
      },
    });
  }
}
