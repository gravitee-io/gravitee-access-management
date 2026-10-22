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
import { ActivatedRoute } from '@angular/router';
import { SortType } from '@swimlane/ngx-datatable';
import { EMPTY, Observable, Subject, Subscription } from 'rxjs';
import { catchError, filter, finalize, switchMap } from 'rxjs/operators';

import { Sort } from '../../../services/api.model';
import { DialogService } from '../../../services/dialog.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { TrustDomainPage, TrustDomainService } from '../../../services/trust-domain.service';

import { keyMaterialSourceLabel, TrustDomain, trustDomainUsages, trustDomainUsagesLabel } from './trust-domain.types';

@Component({
  selector: 'app-domain-trust-domains',
  templateUrl: './trust-domains.component.html',
  styleUrls: ['./trust-domains.component.scss'],
  standalone: false,
})
export class DomainSettingsTrustDomainsComponent implements OnInit, OnDestroy {
  trustDomains: TrustDomain[];
  domainId: string;
  page = { pageNumber: 0, size: 10, totalElements: 0 };
  sort: Sort = { prop: 'name', dir: 'asc' };
  isLoading = false;
  nextCursor: string;
  protected readonly SortType = SortType;
  private readonly loadSubject = new Subject<{ cursor?: string }>();
  private loadSubscription: Subscription;

  constructor(
    private route: ActivatedRoute,
    private trustDomainService: TrustDomainService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.applyPage(this.route.snapshot.data['trustDomains']);
    this.loadSubscription = this.loadSubject
      .pipe(
        switchMap(({ cursor }) => {
          this.isLoading = true;
          this.nextCursor = undefined;
          return this.pageRequest(cursor).pipe(
            finalize(() => (this.isLoading = false)),
            catchError(() => {
              this.snackbarService.open('Failed to load trusted domains');
              return EMPTY;
            }),
          );
        }),
      )
      .subscribe((result) => this.applyPage(result));
  }

  ngOnDestroy() {
    this.loadSubscription?.unsubscribe();
  }

  setPage(pageInfo: { offset: number }) {
    const cursor = this.page.pageNumber + 1 === pageInfo.offset ? this.nextCursor : undefined;
    this.page.pageNumber = pageInfo.offset;
    this.loadSubject.next({ cursor });
  }

  applySort(event: { sorts: Sort[] }) {
    this.sort = event.sorts[0];
    this.page.pageNumber = 0;
    this.loadSubject.next({});
  }

  isEmpty(): boolean {
    return this.page.totalElements === 0;
  }

  usagesLabel(trustDomain: TrustDomain): string {
    return trustDomainUsagesLabel(trustDomain);
  }

  usagesBadgeLabel(trustDomain: TrustDomain): string {
    const usages = trustDomainUsages(trustDomain);
    return usages.length > 1 ? `${usages.length} usages` : trustDomainUsagesLabel(trustDomain);
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
      )
      .subscribe({
        next: () => {
          if (this.trustDomains.length === 1 && this.page.pageNumber > 0) {
            this.page.pageNumber--;
          }
          this.snackbarService.open('Trusted domain deleted');
          this.loadSubject.next({});
        },
        error: () => this.snackbarService.open('Failed to delete trusted domain'),
      });
  }

  private applyPage(result?: TrustDomainPage) {
    this.trustDomains = result?.data ?? [];
    this.page.totalElements = result?.totalCount ?? 0;
    this.nextCursor = result?.nextCursor;
  }

  private pageRequest(cursor?: string): Observable<TrustDomainPage> {
    if (cursor) {
      return this.trustDomainService.next(cursor);
    }
    return this.trustDomainService.list(this.domainId, this.page.pageNumber, this.page.size, { sort: this.sort });
  }
}
