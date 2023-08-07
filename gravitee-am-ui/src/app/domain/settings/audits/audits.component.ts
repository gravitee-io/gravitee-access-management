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
import { FormControl } from '@angular/forms';
import * as _ from 'lodash';
import { Observable } from 'rxjs';
import { filter, map, switchMap } from 'rxjs/operators';

import { AuditService } from '../../../services/audit.service';
import { OrganizationService } from '../../../services/organization.service';
import { UserService } from '../../../services/user.service';
import { AuthService } from '../../../services/auth.service';
import { availableTimeRanges, defaultTimeRangeId } from '../../../utils/time-range-utils';

@Component({
  selector: 'app-audits',
  templateUrl: './audits.component.html',
  styleUrls: ['./audits.component.scss'],
})
export class AuditsComponent implements OnInit {
  private startDateChanged = false;
  private endDateChanged = false;
  organizationContext = false;
  requiredReadPermission: string;
  @ViewChild('auditsTable', { static: true }) table: any;
  userCtrl = new FormControl();
  audits: any[];
  pagedAudits: any;
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
  selectedUser: any;
  selectedTimeRange = defaultTimeRangeId;
  readonly timeRanges = availableTimeRanges;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private auditService: AuditService,
    private organizationService: OrganizationService,
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

  get isEmpty() {
    return !this.audits || (this.audits.length === 0 && !this.displayReset);
  }

  setPage(pageInfo) {
    this.page.pageNumber = pageInfo.offset;
    this.searchAudits();
  }

  isUnknownActor(row) {
    return row.outcome.status === 'FAILURE' && row.type === 'USER_LOGIN';
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

  getTargetUrl(row) {
    const routerLink = [];

    if (row.target.type === 'MEMBERSHIP') {
      // Membership doesn't have link;
      return routerLink;
    }

    if ('organization' === row.target.referenceType) {
      routerLink.push('/settings');
    } else {
      routerLink.push('/environments');
      routerLink.push(this.route.snapshot.paramMap.get('envHrid'));
      routerLink.push('domains');
      routerLink.push(this.route.snapshot.paramMap.get('domainId'));

      if (row.target.type !== 'CLIENT' && row.target.type !== 'APPLICATION') {
        routerLink.push('settings');
      }
    }

    if (row.target.type !== 'DOMAIN') {
      if (row.target.type !== 'IDENTITY_PROVIDER') {
        if (row.target.type === 'CLIENT') {
          routerLink.push('applications');
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
    const selectedTimeRange = _.find(this.timeRanges, { id: this.selectedTimeRange });
    const from = this.startDateChanged
      ? moment(this.startDate).valueOf()
      : moment().subtract(selectedTimeRange.value, selectedTimeRange.unit).valueOf();
    const to = this.endDateChanged ? moment(this.endDate).valueOf() : moment().valueOf();
    const user =
      this.selectedUser ||
      (this.userCtrl.value ? (typeof this.userCtrl.value === 'string' ? this.userCtrl.value : this.userCtrl.value.username) : null);
    this.loadingIndicator = true;
    this.auditService
      .search(
        this.domainId,
        this.page.pageNumber,
        this.page.size,
        this.eventType,
        this.eventStatus,
        user,
        from,
        to,
        this.organizationContext,
      )
      .subscribe((pagedAudits) => {
        this.page.totalElements = pagedAudits.totalCount;
        this.audits = pagedAudits.data
          .map((audit) => this.createActorSortDisplayName(audit))
          .map((audit) => this.createTargetSortDisplayName(audit));
        this.selectedUser = null;
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
      if (row.outcome.status === 'SUCCESS') {
        return JSON.stringify(JSON.parse(row.outcome.message), null, '  ');
      } else {
        return row.outcome.message;
      }
    } else {
      return row.type + ' success';
    }
  }

  onUserSelectionChanged(event) {
    this.selectedUser = event.option.value['username'];
    this.displayReset = true;
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

  hasPermissions(permissions) {
    return this.authService.hasPermissions(permissions);
  }
}
