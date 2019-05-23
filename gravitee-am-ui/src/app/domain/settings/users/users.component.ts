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
import { UserService} from "../../../services/user.service";
import { SnackbarService } from "../../../services/snackbar.service";
import { DialogService } from "../../../services/dialog.service";
import { ActivatedRoute, Router } from "@angular/router";
import { AppConfig } from "../../../../config/app.config";

@Component({
  selector: 'app-users',
  templateUrl: './users.component.html',
  styleUrls: ['./users.component.scss']
})
export class UsersComponent implements OnInit {
  private searchValue: string;
  adminContext: boolean;
  pagedUsers: any;
  users: any[];
  domainId: string;
  page: any = {};

  constructor(private userService: UserService, private dialogService: DialogService,
              private snackbarService: SnackbarService, private route: ActivatedRoute, private router: Router) {
    this.page.pageNumber = 0;
    this.page.size = 25;
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.domainId = AppConfig.settings.authentication.domainId;
      this.adminContext = true;
    }
    this.pagedUsers = this.route.snapshot.data['users'];
    this.users = this.pagedUsers.data;
    this.page.totalElements = this.pagedUsers.totalCount;
  }

  get isEmpty() {
    return !this.users || this.users.length == 0 && !this.searchValue;
  }

  loadUsers() {
    let findUsers = (this.searchValue) ?
      this.userService.search(this.domainId, this.searchValue, this.page.pageNumber, this.page.size) :
      this.userService.findByDomain(this.domainId, this.page.pageNumber, this.page.size)

    findUsers.map(res => res.json()).subscribe(pagedUsers => {
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
            this.snackbarService.open("User deleted");
            this.page.pageNumber = 0;
            this.loadUsers();
          });
        }
      });
  }

  onSearch(event) {
    this.searchValue = event.target.value;
    this.loadUsers();
  }

  setPage(pageInfo){
    this.page.pageNumber = pageInfo.offset;
    this.loadUsers();
  }

  accountLocked(user) {
    return !user.accountNonLocked && user.accountLockedUntil > new Date();
  }
}
