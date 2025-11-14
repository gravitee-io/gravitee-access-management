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
import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import moment from 'moment';
import { UntypedFormControl } from '@angular/forms';
import { Observable } from 'rxjs';
import { filter, map, switchMap } from 'rxjs/operators';
import { find } from 'lodash';

import { AuditService } from '../../../services/audit.service';
import { OrganizationService } from '../../../services/organization.service';
import { UserService } from '../../../services/user.service';
import { AuthService } from '../../../services/auth.service';
import { availableTimeRanges, defaultTimeRangeId } from '../../../utils/time-range-utils';
import { EnvironmentService } from '../../../services/environment.service';

@Component({
  selector: 'app-audits',
  templateUrl: './audits.component.html',
  styleUrls: ['./audits.component.scss'],
  standalone: false,
})
export class AuditsComponent implements OnInit {
  private startDateChanged = false;
  private endDateChanged = false;
  organizationContext = false;
  requiredReadPermission: string;
  @ViewChild('auditsTable', { static: true }) table: any;
  userCtrl = new UntypedFormControl();
  audits: any[];
  domainId: string;
  page: any = {};
  eventTypes: string[];
  eventType: string;
  eventStatus: string;
  startDate: any;
  endDate: any;
  displayReset = false;
  expanded: any = {};
  loadingIndicator: boolean;
  config: any = { lineWrapping: true, lineNumbers: true, readOnly: true, mode: 'application/json' };
  filteredUsers$: Observable<any[]>;
  selectedUser: string;
  selectedTimeRange = defaultTimeRangeId;
  readonly timeRanges = availableTimeRanges;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private auditService: AuditService,
    private organizationService: OrganizationService,
    private environmentService: EnvironmentService,
    private userService: UserService,
    private authService: AuthService,
  ) {
    this.page.pageNumber = 0;
    this.page.size = 10;
    this.filteredUsers$ = this.userCtrl.valueChanges.pipe(
      filter((searchTerm) => typeof searchTerm === 'string'),
      switchMap((searchTerm) => this.userService.search(this.domainId, 'q=' + searchTerm + '*', 0, 30, this.organizationContext)),
      map((response) => response.data),
    );
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
      this.requiredReadPermission = 'organization_audit_read';
    } else {
      this.requiredReadPermission = 'domain_audit_read';
    }
    // load event types
    this.organizationService.auditEventTypes().subscribe((data) => (this.eventTypes = data));
    // load audits
    this.search();
  }

  setPage(pageInfo) {
    this.page.pageNumber = pageInfo.offset;
    this.searchAudits();
  }

  isUnknownActor(row): boolean {
    return row.actor?.id === undefined;
  }

  hasActorUrl(row) {
    return this.getActorUrl(row).length > 0;
  }

  getActorUrl(row) {
    const routerLink = [];

    if ('organization' === row.actor.referenceType) {
      routerLink.push('/settings');
    } else if ('domain' === row.actor.referenceType) {
      routerLink.push('..');
    }

    if (routerLink.length > 0) {
      routerLink.push('users');
      routerLink.push(row.actor.id);
    }

    return routerLink;
  }

  getTargetUrl(row): string[] {
    if (row.target.type === 'MEMBERSHIP') {
      // Membership doesn't have link
      return [];
    }
    return this.buildLink(row);
  }

  private buildLink(row) {
    const routerLink = [];
    if ('organization' === row.target.referenceType) {
      routerLink.push('/settings');
    } else {
      routerLink.push(...this.buildNotOrganizationLink(row));
    }
    routerLink.push(...this.buildNotDomainLink(row));
    return routerLink;
  }

  private buildNotOrganizationLink(row) {
    const routerLink = [];
    if (this.isDomainAuditOnOrganizationLevel(row)) {
      // For now, we do not provide a link to the domain when it comes from the organization audits
      // as for doing so we need the env and the domain HRID. To get these information we have to manage
      // backend requests based on the env & domain internal ids.
      return routerLink;
    }
    routerLink.push('/environments');
    routerLink.push(this.route.snapshot.paramMap.get('envHrid'));
    routerLink.push('domains');
    routerLink.push(this.route.snapshot.paramMap.get('domainId'));

    if (row.target.type !== 'CLIENT' && row.target.type !== 'APPLICATION' && row.target.type !== 'PROTECTED_RESOURCE') {
      routerLink.push('settings');
    }
    return routerLink;
  }

  isDomainAuditOnOrganizationLevel(row): boolean {
    return row.referenceType === 'organization' && row.target.referenceType === 'environment' && row.target.type === 'DOMAIN';
  }

  private buildNotDomainLink(row) {
    const routerLink = [];
    if (row.target.type !== 'DOMAIN') {
      if (row.target.type !== 'IDENTITY_PROVIDER') {
        if (row.target.type === 'PASSWORD_POLICY') {
          routerLink.push('password-policies');
        } else if (row.target.type === 'CLIENT') {
          routerLink.push('applications');
        } else if (row.target.type === 'PROTECTED_RESOURCE') {
          routerLink.push('mcp-servers');
        } else if (row.target.type === 'CREDENTIAL') {
          // Credentials are user-scoped, route path is stored in target.attributes (set by backend audit builders)
          // The routePath is relative (e.g., ['users', userId, 'cert-credentials', credentialId])
          // and will be prepended with domain path by buildLink()
          const routePath = row.target.attributes?.routePath;
          return Array.isArray(routePath) ? [...routePath] : [];
        } else {
          routerLink.push(row.target.type.toLowerCase() + 's');
        }
      } else {
        routerLink.push('providers');
      }
      if (row.target.type === 'FORM' || row.target.type === 'EMAIL') {
        routerLink.push(row.target.type.toLowerCase());
      } else {
        routerLink.push(row.target.id);
      }
    }
    return routerLink;
  }

  getTargetParams(row) {
    const params = {};
    if (row.target.type === 'FORM' || row.target.type === 'EMAIL') {
      params['template'] = row.target.displayName.toUpperCase();
    }
    return params;
  }

  search() {
    this.page.pageNumber = 0;
    this.searchAudits();
  }

  startDateChange() {
    this.startDateChanged = true;
    this.updateForm();
  }

  endDateChange() {
    this.endDateChanged = true;
    this.updateForm();
  }

  updateForm() {
    this.displayReset = true;
  }

  resetForm() {
    this.page.pageNumber = 0;
    this.eventType = null;
    this.eventStatus = null;
    this.startDateChanged = false;
    this.startDate = null;
    this.endDate = null;
    this.endDateChanged = false;
    this.displayReset = false;
    this.selectedUser = null;
    this.userCtrl.reset();
    this.searchAudits();
  }

  refresh() {
    this.searchAudits();
  }

  searchAudits() {
    const selectedTimeRange = find(this.timeRanges, { id: this.selectedTimeRange });
    const from = this.startDateChanged
      ? moment(this.startDate).valueOf()
      : moment().subtract(selectedTimeRange.value, selectedTimeRange.unit).valueOf();
    const to = this.endDateChanged ? moment(this.endDate).valueOf() : moment().valueOf();
    let userId: string = null;
    if (this.selectedUser) {
      userId = this.selectedUser;
    } else if (this.userCtrl.value) {
      userId = typeof this.userCtrl.value === 'string' ? this.userCtrl.value : this.userCtrl.value.username;
    }
    this.loadingIndicator = true;
    const searchParams = {
      domainId: this.domainId,
      page: this.page.pageNumber,
      size: this.page.size,
      type: this.eventType,
      status: this.eventStatus,
      userId: userId,
      from: from,
      to: to,
    };
    this.auditService.search(searchParams, this.organizationContext).subscribe((pagedAudits) => {
      this.page.totalElements = pagedAudits.totalCount;
      this.audits = pagedAudits.data
        .map((audit) => this.createActorSortDisplayName(audit))
        .map((audit) => this.createTargetSortDisplayName(audit));
      this.loadingIndicator = false;
    });
  }

  private createTargetSortDisplayName(audit) {
    if (audit.target) {
      audit.target.sortDisplayName = audit.target.displayName;
      if (audit.target.alternativeId) {
        audit.target.sortDisplayName += ` | ${audit.target?.alertnativeId}`;
      }
    }
    return audit;
  }

  private createActorSortDisplayName(audit) {
    if (audit.actor) {
      if (this.isUnknownActor(audit)) {
        audit.actor.sortDisplayName = audit.actor.alternativeId;
      } else if (this.hasActorUrl(audit)) {
        audit.actor.sortDisplayName = `${audit.actor.displayName} | ${audit.actor?.displayName}`;
      } else {
        audit.actor.sortDisplayName = audit.actor.displayName;
      }
    }
    return audit;
  }

  toggleExpandRow(row) {
    this.table.rowDetail.toggleExpandRow(row);
  }

  auditDetails(row) {
    if (row.outcome.message) {
      if (row.outcome.status === 'success') {
        return JSON.stringify(JSON.parse(row.outcome.message), null, '  ');
      } else {
        return row.outcome.message;
      }
    } else {
      return row.type + ' ' + row.outcome.status;
    }
  }

  onUserSelectionChanged(event) {
    if (this.selectedUser === event.option.value['username']) {
      this.clearUserInput();
    } else {
      this.selectedUser = event.option.value['username'];
      this.displayReset = true;
    }
  }

  displayUserFn(user?: any): string | undefined {
    return user ? user.username : undefined;
  }

  displayUserName(user) {
    if (user.firstName) {
      return user.firstName + ' ' + (user.lastName ? user.lastName : '');
    } else {
      return user.username;
    }
  }

  clearUserInput() {
    this.selectedUser = null;
    this.userCtrl.reset();
  }

  hasPermissions(permissions: string[]) {
    const effectivePermissions = permissions.map((acl) => (this.organizationContext ? 'organization_reporter_' : 'domain_reporter_') + acl);
    return this.authService.hasPermissions(effectivePermissions);
  }

  hasLinkableTarget(row: any) {
    const isSuccessfulDeleteEvent = row.outcome.status === 'success' && this.isDeletionEventType(row.type);
    const hasValidUrl = this.getTargetUrl(row).length > 0;
    return !this.isDomainAuditOnOrganizationLevel(row) && !isSuccessfulDeleteEvent && hasValidUrl;
  }

  private isDeletionEventType(type: string): boolean {
    const eventType = type.toLowerCase();
    return eventType.includes('deleted') || eventType.includes('revoked');
  }
}
