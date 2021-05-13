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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {RoleService} from '../../../services/role.service';
import {DialogService} from '../../../services/dialog.service';
import {SnackbarService} from '../../../services/snackbar.service';

@Component({
  selector: 'app-roles',
  templateUrl: './roles.component.html',
  styleUrls: ['./roles.component.scss']
})
export class DomainSettingsRolesComponent implements OnInit {
  private searchValue: string;
  roles: any[];
  domainId: string;
  page: any = {};

  constructor(private roleService: RoleService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute) {
    this.page.pageNumber = 0;
    this.page.size = 10;
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    const pagedRoles = this.route.snapshot.data['roles'];
    this.roles = pagedRoles.data;
    this.page.totalElements = pagedRoles.totalCount;
  }

  get isEmpty() {
    return !this.roles || this.roles.length === 0 && !this.searchValue;
  }

  loadRoles() {
    const findRoles = (this.searchValue) ?
      this.roleService.search('*' + this.searchValue + '*',this.domainId, this.page.pageNumber, this.page.size) :
      this.roleService.findByDomain(this.domainId, this.page.pageNumber, this.page.size);


    findRoles.subscribe(pagedScopes => {
      this.page.totalElements = pagedScopes.totalCount;
      this.roles = pagedScopes.data;
    });
  }

  onSearch(event) {
    this.page.pageNumber = 0;
    this.searchValue = event.target.value;
    this.loadRoles();
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Role', 'Are you sure you want to delete this role ?')
      .subscribe(res => {
        if (res) {
          this.roleService.delete(this.domainId, id).subscribe(response => {
            this.snackbarService.open('Role deleted');
            this.loadRoles();
          });
        }
      });
  }

  setPage(pageInfo) {
    this.page.pageNumber = pageInfo.offset;
    this.loadRoles();
  }

}
