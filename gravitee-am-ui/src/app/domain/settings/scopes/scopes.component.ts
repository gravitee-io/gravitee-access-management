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
import { SnackbarService } from "../../../services/snackbar.service";
import { DialogService } from "../../../services/dialog.service";
import { ActivatedRoute } from "@angular/router";
import { ScopeService } from "../../../services/scope.service";
import moment from 'moment';
import {AuthService} from "../../../services/auth.service";

@Component({
  selector: 'app-scopes',
  templateUrl: './scopes.component.html',
  styleUrls: ['./scopes.component.scss']
})
export class DomainSettingsScopesComponent implements OnInit {
  private searchValue: string;
  page: any = {};
  private scopes: any[];
  domainId: string;
  canDelete: boolean;
  canEdit: boolean;

  constructor(private scopeService: ScopeService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private authService: AuthService,
              private route: ActivatedRoute) {
    this.page.pageNumber = 0;
    this.page.size = 10;
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    const pagedScopes = this.route.snapshot.data['scopes'];
    this.scopes = pagedScopes.data;
    this.page.totalElements = pagedScopes.totalCount
    this.canDelete = this.authService.hasPermissions(['domain_scope_delete']);
    this.canEdit = this.authService.hasPermissions(['domain_scope_update']);
  }

  onSearch(event) {
    this.page.pageNumber = 0;
    this.searchValue = event.target.value;
    this.loadScopes();
  }

  loadScopes() {
    const findScopes = (this.searchValue) ?
      this.scopeService.search('*' + this.searchValue + '*',this.domainId, this.page.pageNumber, this.page.size) :
      this.scopeService.findByDomain(this.domainId, this.page.pageNumber, this.page.size);


    findScopes.subscribe(pagedScopes => {
      this.page.totalElements = pagedScopes.totalCount;
      this.scopes = pagedScopes.data;
    });
  }

  get isEmpty() {
    return !this.scopes || this.scopes.length === 0 && !this.searchValue;
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Scope', 'Are you sure you want to delete this scope ?')
      .subscribe(res => {
        if (res) {
          this.scopeService.delete(this.domainId, id).subscribe(response => {
            this.snackbarService.open("Scope deleted");
            this.loadScopes();
          });
        }
      });
  }

  setPage(pageInfo) {
    this.page.pageNumber = pageInfo.offset;
    this.loadScopes();
  }

  getScopeExpiry(expiresIn) {
    return expiresIn ? moment.duration(expiresIn, 'seconds').humanize() : 'no time set';
  }

  enableScopeDiscovery(id, event) {
    this.scopeService.patchDiscovery(this.domainId, id, event.checked).subscribe(response => {
      this.snackbarService.open('Scope updated');
      this.loadScopes();
    });
  }
}
