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
import { ActivatedRoute, Router } from "@angular/router";
import { AppConfig } from "../../../../config/app.config";
import { AuditService } from "../../../services/audit.service";
import * as moment from 'moment';
import { PlatformService } from "../../../services/platform.service";
import { UserService } from "../../../services/user.service";
import { FormControl } from "@angular/forms";

@Component({
  selector: 'app-audits',
  templateUrl: './audits.component.html',
  styleUrls: ['./audits.component.scss']
})
export class AuditsComponent implements OnInit {
  @ViewChild('auditsTable') table: any;
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
  displayReset: boolean = false;
  expanded: any = {};
  config: any = {lineWrapping:true, lineNumbers: true, readOnly: true, mode: 'application/json'};
  filteredUsers: any[];
  private startDateChanged: boolean = false;
  private endDateChanged: boolean = false;
  selectedUser: any;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private auditService: AuditService,
              private platformService: PlatformService,
              private userService: UserService) {
    this.page.pageNumber = 0;
    this.page.size = 10;

    this.userCtrl.valueChanges
      .subscribe(searchTerm => {
        this.userService.search(this.domainId, searchTerm, 0, 30).subscribe(response => {
          this.filteredUsers = response.data;
        });
      });
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.domainId = AppConfig.settings.authentication.domainId;
    }

    this.pagedAudits = this.route.snapshot.data['audits'];
    this.audits = this.pagedAudits.data;
    this.page.totalElements = this.pagedAudits.totalCount;

    // load event types
    this.platformService.auditEventTypes().subscribe(data => this.eventTypes = data);
  }

  get isEmpty() {
    return !this.audits || this.audits.length == 0 && !this.displayReset;
  }

  setPage(pageInfo){
    this.page.pageNumber = pageInfo.offset;
    this.searchAudits();
  }

  isUnknownActor(row) {
    return row.outcome.status === 'FAILURE' && row.type === 'USER_LOGIN';
  }

  getActorUrl(row) {
    let routerLink = [];

    if (AppConfig.settings.authentication.domainId === row.actor.domain || AppConfig.settings.authentication.domainId === row.accessPoint.id) {
      routerLink.push('/settings');
      routerLink.push('management');
    } else {
      routerLink.push('/domains');
      routerLink.push(row.actor.domain ? row.actor.domain : row.domain);
      routerLink.push('settings');
    }

    routerLink.push('users');
    routerLink.push(row.actor.id);

    return routerLink;
  }

  getTargetUrl(row) {
    let routerLink = [];
    if (AppConfig.settings.authentication.domainId === row.target.domain) {
      routerLink.push('/settings');
      routerLink.push('management');
    } else {
      routerLink.push('/domains');
      routerLink.push(row.target.domain);
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
    let params = {};
    if (row.target.type === 'FORM' || row.target.type === 'EMAIL') {
      params['template'] = row.target.displayName.toUpperCase();
    }
    return params;
  }

  search(event) {
    event.preventDefault();
    this.page.pageNumber = 0;
    this.searchAudits();
  }

  startDateChange(element, event) {
    this.startDateChanged = true;
    this.updateForm(element, event);
  }

  endDateChange(element, event) {
    this.endDateChanged = true;
    this.updateForm(element, event);
  }

  updateForm(element, event) {
    this.displayReset = true;
  }

  resetForm() {
    this.page.pageNumber = 0;
    this.eventType = null;
    this.eventStatus = null;
    this.startDate = null;
    this.endDate = null;
    this.displayReset = false;
    this.selectedUser = null;
    this.userCtrl.reset();
    this.searchAudits();
  }

  refresh() {
    this.searchAudits();
  }

  searchAudits() {
    let from = this.startDateChanged ? moment(this.startDate).valueOf() : null;
    let to = this.endDateChanged ? moment(this.endDate).valueOf() : null;
    this.auditService.search(this.domainId, this.page.pageNumber, this.page.size, this.eventType, this.eventStatus, this.selectedUser, from, to).subscribe(pagedAudits => {
      this.page.totalElements = pagedAudits.totalCount;
      this.audits = pagedAudits.data;
    });
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
    this.selectedUser = event.option.value["id"];
    this.displayReset = true;
  }


  displayUserFn(user?: any): string | undefined {
    return user ? user.username : undefined;
  }

  displayUserName(user) {
    if (user.firstName) {
      return user.firstName + " " + (user.lastName ? user.lastName : '');
    } else {
      return user.username;
    }
  }

  clearUserInput() {
    this.selectedUser = null;
    this.userCtrl.reset();
  }
}
