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
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { UserService} from '../../../services/user.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DialogService } from '../../../services/dialog.service';
import { OrganizationService } from '../../../services/organization.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-users',
  templateUrl: './users.component.html',
  styleUrls: ['./users.component.scss']
})
export class UsersComponent implements OnInit {
  private searchValue: string;
  private isLoading: boolean;
  private hasValue: boolean;
  organizationContext: boolean;
  requiredReadPermission: string;
  pagedUsers: any;
  users: any[];
  domainId: string;
  page: any = {};
  createMode: boolean;
  searchMode = 'standard';

  constructor(private userService: UserService,
              private organizationService: OrganizationService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private authService: AuthService,
              private route: ActivatedRoute,
              private router: Router,
              public dialog: MatDialog) {
    this.page.pageNumber = 0;
    this.page.size = 25;
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
      this.createMode = false;
      this.requiredReadPermission = 'organization_user_read';
    } else {
      this.createMode = this.authService.hasPermissions(['domain_user_create']);
      this.requiredReadPermission = 'domain_user_read';
    }
    this.pagedUsers = this.route.snapshot.data['users'];
    this.users = this.pagedUsers.data;
    this.page.totalElements = this.pagedUsers.totalCount;
    this.hasValue = this.pagedUsers.totalCount > 0;
  }

  get isEmpty() {
    return !this.users || this.users.length === 0 && (!this.searchValue && !this.hasValue) && !this.isLoading;
  }

  loadUsers() {
    let findUsers;
    if (this.searchValue) {
      const searchTerm = this.searchMode === 'standard' ? 'q=' + this.searchValue + '*' : 'filter=' + this.searchValue;
      findUsers = this.userService.search(this.domainId, searchTerm, this.page.pageNumber, this.page.size, this.organizationContext);
    } else {
      findUsers = this.organizationContext
        ? this.organizationService.users(this.page.pageNumber, this.page.size)
        : this.userService.findByDomain(this.domainId, this.page.pageNumber, this.page.size);
    }
    this.isLoading = true;
    findUsers.subscribe(pagedUsers => {
      this.isLoading = false;
      this.page.totalElements = pagedUsers.totalCount;
      this.users = pagedUsers.data;
    });
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete User', 'Are you sure you want to delete this user ?')
      .subscribe(res => {
        if (res) {
          this.userService.delete(this.domainId, id).subscribe(response => {
            this.snackbarService.open('User deleted');
            this.page.pageNumber = 0;
            this.loadUsers();
          });
        }
      });
  }

  discardSearchValue() {
    this.searchValue = null;
    this.loadUsers();
  }

  onSearch() {
    this.loadUsers();
  }

  setPage(pageInfo) {
    this.page.pageNumber = pageInfo.offset;
    this.loadUsers();
  }

  accountLocked(user) {
    return !user.accountNonLocked && user.accountLockedUntil > new Date();
  }

  hasReadPermissions(): boolean {
    return this.authService.hasPermissions([this.requiredReadPermission]);
  }

  openDialog() {
    this.dialog.open(UsersSearchInfoDialog, {});
  }
}

@Component({
  selector: 'users-search-info-dialog',
  templateUrl: './dialog/users-search-info.component.html',
})
export class UsersSearchInfoDialog {
  constructor(public dialogRef: MatDialogRef<UsersSearchInfoDialog>) {}
}

