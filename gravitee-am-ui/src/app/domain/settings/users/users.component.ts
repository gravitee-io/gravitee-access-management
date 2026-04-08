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
import { filter, switchMap, tap } from 'rxjs/operators';

import { UserService } from '../../../services/user.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DialogService } from '../../../services/dialog.service';
import { OrganizationService } from '../../../services/organization.service';
import { AuthService } from '../../../services/auth.service';
import { ApplicationService } from '../../../services/application.service';
import { ProviderService } from '../../../services/provider.service';

@Component({
  selector: 'app-users',
  templateUrl: './users.component.html',
  styleUrls: ['./users.component.scss'],
  standalone: false,
})
export class UsersComponent implements OnInit {
  private readonly scimOperatorPattern = /"(?:\\.|[^"\\])*"|\b[\w.-]+\s+(?<op>pr|eq|ne|co|sw|ew|gt|ge|lt|le)\b/gi;

  isLoading: boolean;
  searchValue: string;
  organizationContext: boolean;
  requiredReadPermission: string;
  users: any[];
  domainId: string;
  createMode: boolean;
  applications: any[];
  identityProviders: any[];
  selectedIdPs: string[];
  selectedApplications: string[];
  selectedDisabledUsers = false;
  displayAdvancedSearchMode = false;

  // Cursor pagination state
  pageSize = 25;
  totalCount = 0;
  hasNext = false;
  hasPrevious = false;
  private nextCursor: string | null = null;
  private cursorStack: string[] = [];
  private currentSort: string = 'username';

  // Fallback offset pagination for organization context (no cursor endpoint)
  page: any = {};

  constructor(
    private userService: UserService,
    private organizationService: OrganizationService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private applicationService: ApplicationService,
    private providerService: ProviderService,
    private route: ActivatedRoute,
    private router: Router,
    public dialog: MatDialog,
  ) {
    this.page.pageNumber = 0;
    this.page.size = 25;
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
      this.createMode = this.authService.hasPermissions(['organization_user_create']);
      this.requiredReadPermission = 'organization_user_read';
    } else {
      this.createMode = this.authService.hasPermissions(['domain_user_create']);
      this.requiredReadPermission = 'domain_user_read';
    }
    this.loadUsers(null);
  }

  loadUsers(searchQuery: string | null) {
    if (this.organizationContext) {
      this.loadUsersOffset(searchQuery);
      return;
    }

    const currentCursor = this.cursorStack.length > 0 ? this.cursorStack[this.cursorStack.length - 1] : undefined;
    let findUsers: any;
    let advancedSearchMode = false;

    if (searchQuery) {
      advancedSearchMode = this.isAdvancedSearch(searchQuery);
      const searchTerm = !advancedSearchMode ? 'q=' + searchQuery + '*' : 'filter=' + encodeURIComponent(searchQuery);
      findUsers = this.userService.searchCursor(this.domainId, searchTerm, this.pageSize, currentCursor, this.currentSort);
    } else {
      findUsers = this.userService.findByDomainCursor(this.domainId, this.pageSize, currentCursor, this.currentSort);
    }

    this.isLoading = true;
    findUsers.subscribe((cursorPage) => {
      this.isLoading = false;
      this.users = cursorPage.data;
      this.nextCursor = cursorPage.nextCursor;
      this.hasNext = cursorPage.hasNext;
      this.hasPrevious = this.cursorStack.length > 0;
      if (cursorPage.totalCount !== undefined) {
        this.totalCount = cursorPage.totalCount;
      }
      if (advancedSearchMode) {
        this.searchValue = searchQuery;
      }
    });
  }

  /** Fallback offset pagination for organization context */
  private loadUsersOffset(searchQuery: string | null) {
    let findUsers: any;
    let advancedSearchMode = false;
    if (searchQuery) {
      advancedSearchMode = this.isAdvancedSearch(searchQuery);
      const searchTerm = !advancedSearchMode ? 'q=' + searchQuery + '*' : 'filter=' + encodeURIComponent(searchQuery);
      findUsers = this.userService.search(this.domainId, searchTerm, this.page.pageNumber, this.page.size, this.organizationContext);
    } else {
      findUsers = this.organizationService.users(this.page.pageNumber, this.page.size);
    }
    this.isLoading = true;
    findUsers.subscribe((pagedUsers) => {
      this.isLoading = false;
      this.page.totalElements = pagedUsers.totalCount;
      this.users = pagedUsers.data;
      if (advancedSearchMode) {
        this.searchValue = searchQuery;
      }
    });
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete User', 'Are you sure you want to delete this user ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.userService.delete(this.domainId, id, this.organizationContext)),
        tap(() => {
          this.snackbarService.open('User deleted');
          this.resetPagination();
          this.loadUsers(null);
        }),
      )
      .subscribe();
  }

  openAdvancedSearch() {
    this.displayAdvancedSearchMode = !this.displayAdvancedSearchMode;
    if (this.displayAdvancedSearchMode) {
      this.applicationService.findByDomain(this.domainId, 0, 50).subscribe((response) => {
        this.applications = response.data;
      });
      this.providerService.findByDomain(this.domainId).subscribe((response) => {
        this.identityProviders = response;
      });
    }
  }

  closeAdvancedSearch() {
    this.displayAdvancedSearchMode = false;
  }

  discardSearchValue() {
    this.searchValue = null;
    this.selectedApplications = null;
    this.selectedIdPs = null;
    this.selectedDisabledUsers = false;
    this.resetPagination();
    this.loadUsers(this.searchValue);
  }

  onSearch() {
    this.resetPagination();
    this.loadUsers(this.searchValue);
  }

  onAdvancedSearch() {
    this.resetPagination();
    this.displayAdvancedSearchMode = false;
    let searchQuery = '';
    if (this.selectedApplications && this.selectedApplications.length > 0) {
      if (searchQuery.length > 1) {
        searchQuery += ' and ';
      }
      searchQuery += this.selectedApplications.map((app) => 'client eq "' + app + '"').join(' or ');
    }
    if (this.selectedIdPs && this.selectedIdPs.length > 0) {
      if (searchQuery.length > 1) {
        searchQuery += ' and ';
      }
      searchQuery += this.selectedIdPs.map((idp) => 'source eq "' + idp + '"').join(' or ');
    }
    if (this.selectedDisabledUsers) {
      if (searchQuery.length > 1) {
        searchQuery += ' and ';
      }
      searchQuery += 'enabled eq false';
    }
    if (searchQuery) {
      this.loadUsers(searchQuery);
    }
  }

  toggleDisabledUsers(event) {
    this.selectedDisabledUsers = event.checked;
  }

  nextPage() {
    if (this.organizationContext) {
      this.page.pageNumber++;
      this.loadUsers(this.searchValue);
    } else if (this.nextCursor) {
      this.cursorStack.push(this.nextCursor);
      this.loadUsers(this.searchValue);
    }
  }

  previousPage() {
    if (this.organizationContext) {
      this.page.pageNumber = Math.max(0, this.page.pageNumber - 1);
      this.loadUsers(this.searchValue);
    } else if (this.cursorStack.length > 0) {
      this.cursorStack.pop();
      this.loadUsers(this.searchValue);
    }
  }

  onSort(event) {
    if (this.organizationContext) return; // org context uses offset pagination, no server sort
    const sortField = event.sorts[0];
    const prop = sortField.prop === 'username' ? 'username' : 'updatedAt';
    this.currentSort = sortField.dir === 'desc' ? '-' + prop : prop;
    this.resetPagination();
    this.loadUsers(this.searchValue);
  }

  setPage(pageInfo) {
    // Kept for organization context fallback via ngx-datatable paging events
    this.page.pageNumber = pageInfo.offset;
    this.loadUsers(this.searchValue);
  }

  private resetPagination() {
    this.cursorStack = [];
    this.nextCursor = null;
    this.hasNext = false;
    this.hasPrevious = false;
    this.page.pageNumber = 0;
  }

  accountLocked(user) {
    return !user.accountNonLocked && (user.accountLockedUntil === null || !user.accountLockedUntil || user.accountLockedUntil > new Date());
  }

  hasReadPermissions(): boolean {
    return this.authService.hasPermissions([this.requiredReadPermission]);
  }

  openDialog() {
    this.dialog.open(UsersSearchInfoDialogComponent, {});
  }

  private isAdvancedSearch(searchQuery: string): boolean {
    this.scimOperatorPattern.lastIndex = 0;
    let match;
    while ((match = this.scimOperatorPattern.exec(searchQuery))) {
      if (match.groups?.op) return true;
    }
    return false;
  }
}

@Component({
  selector: 'users-search-info-dialog',
  templateUrl: './dialog/users-search-info.component.html',
  standalone: false,
})
export class UsersSearchInfoDialogComponent {
  constructor(public dialogRef: MatDialogRef<UsersSearchInfoDialogComponent>) {}
}
