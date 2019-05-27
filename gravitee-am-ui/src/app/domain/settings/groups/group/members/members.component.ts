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
import { Component, Inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from "@angular/router";
import { GroupService } from "../../../../../services/group.service";
import { AppConfig } from "../../../../../../config/app.config";
import { DialogService } from "../../../../../services/dialog.service";
import { SnackbarService } from "../../../../../services/snackbar.service";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material";
import { FormControl } from "@angular/forms";
import { UserService } from "../../../../../services/user.service";

@Component({
  selector: 'app-group-members',
  templateUrl: './members.component.html',
  styleUrls: ['./members.component.scss']
})
export class GroupMembersComponent implements OnInit {
  private domainId: string;
  group: any;
  members: any[];
  page: any = {};

  constructor(private route: ActivatedRoute,
              private router: Router,
              private groupService: GroupService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private dialog: MatDialog) {
    this.page.pageNumber = 0;
    this.page.size = 25;
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.domainId = AppConfig.settings.authentication.domainId;
    }
    this.group = this.route.snapshot.parent.data['group'];
    this.loadMembers();
  }

  get isEmpty() {
    return !this.members || this.members.length == 0;
  }

  loadMembers() {
    this.groupService.findMembers(this.domainId, this.group.id,  this.page.pageNumber,  this.page.size)
      .subscribe(pagedMembers => {
        this.page.totalElements = pagedMembers.totalCount;
        this.members = Object.assign([], pagedMembers.data);
      });
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Remove Member', 'Are you sure you want to remove this member ?')
      .subscribe(res => {
        if (res) {
          var index = this.group.members.indexOf(id);
          if (index > -1) {
            this.group.members.splice(index, 1);
          }
          this.update("Member deleted")
        }
      });
  }

  setPage(pageInfo){
    this.page.pageNumber = pageInfo.offset;
    this.loadMembers();
  }

  add() {
    let dialogRef = this.dialog.open(AddMemberComponent, { width : '700px', data: { domain: this.domainId }});
    dialogRef.afterClosed().subscribe(member => {
      if (member) {
        if (!this.group.members || !this.group.members.includes(member.id)) {
          (this.group.members = this.group.members || []).push(member.id);
          this.update("Member added");
        } else {
          this.snackbarService.open(`Error : member ${member.username} already exists`);
        }
      }
    });
  }

  private update(message) {
    this.groupService.update(this.domainId, this.group.id, this.group).subscribe(data => {
      this.group = data;
      this.loadMembers();
      this.snackbarService.open(message);
    });
  }
}

@Component({
  selector: 'add-member',
  templateUrl: './add/add-member.component.html',
})
export class AddMemberComponent {
  memberCtrl = new FormControl();
  filteredUsers: any[];
  selectedUser: any;

  constructor(@Inject(MAT_DIALOG_DATA) public data: any,
              public dialogRef: MatDialogRef<AddMemberComponent>,
              private userService: UserService) {
    this.memberCtrl.valueChanges
      .subscribe(searchTerm => {
        this.userService.search(data.domain, searchTerm, 0, 30).subscribe(response => {
          this.filteredUsers = response.data;
        });
      });
  }

  onSelectionChanged(event) {
    this.selectedUser = event.option.value;
  }

  displayFn(user?: any): string | undefined {
    return user ? user.username : undefined;
  }

  displayName(user) {
    if (user.firstName) {
      return user.firstName + " " + (user.lastName ? user.lastName : '');
    } else {
      return user.username;
    }
  }
}
