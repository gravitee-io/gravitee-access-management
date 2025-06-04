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
import { Component, ElementRef, Inject, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { UntypedFormControl } from '@angular/forms';
import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { filter, switchMap, tap } from 'rxjs/operators';
import { map } from 'lodash';

import { UserService } from '../../../../../services/user.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { DialogService } from '../../../../../services/dialog.service';
import { GroupService } from '../../../../../services/group.service';
import { OrganizationService } from '../../../../../services/organization.service';
import { AuthService } from '../../../../../services/auth.service';

@Component({
  selector: 'app-group-members',
  templateUrl: './members.component.html',
  styleUrls: ['./members.component.scss'],
  standalone: false,
})
export class GroupMembersComponent implements OnInit {
  private domainId: string;
  private organizationContext = false;
  group: any;
  members: any[];
  page: any = {};
  editMode: boolean;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private groupService: GroupService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private organizationService: OrganizationService,
    private authService: AuthService,
    private dialog: MatDialog,
  ) {
    this.page.pageNumber = 0;
    this.page.size = 25;
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
      this.editMode = this.authService.hasPermissions(['organization_group_update']);
    } else {
      this.editMode = this.authService.hasPermissions(['domain_group_update']);
    }
    this.group = this.route.snapshot.data['group'];
    const pagedMembers = this.route.snapshot.data['members'];
    this.page.totalElements = pagedMembers.totalCount;
    this.members = Object.assign([], pagedMembers.data);
  }

  get isEmpty() {
    return !this.members || this.members.length === 0;
  }

  loadMembers() {
    const findMembers = this.organizationContext
      ? this.organizationService.groupMembers(this.group.id)
      : this.groupService.findMembers(this.domainId, this.group.id, this.page.pageNumber, this.page.size);

    findMembers.subscribe((pagedMembers) => {
      this.page.totalElements = pagedMembers.totalCount;
      this.members = Object.assign([], pagedMembers.data);
    });
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Remove Member', 'Are you sure you want to remove this member ?')
      .pipe(
        filter((res) => res),
        tap(() => {
          const index = this.group.members.indexOf(id);
          if (index > -1) {
            this.group.members.splice(index, 1);
          }
          this.update('Member deleted');
        }),
      )
      .subscribe();
  }

  setPage(pageInfo) {
    this.page.pageNumber = pageInfo.offset;
    this.loadMembers();
  }

  add() {
    const dialogRef = this.dialog.open(AddMemberComponent, {
      width: '700px',
      data: { domain: this.domainId, organizationContext: this.organizationContext, groupMembers: this.group.members },
    });
    dialogRef.afterClosed().subscribe((members) => {
      if (members) {
        const memberIds = map(members, 'id');
        this.group.members = (this.group.members || []).concat(memberIds);
        this.update('Member(s) added');
      }
    });
  }

  accountLocked(user) {
    return !user.accountNonLocked && user.accountLockedUntil > new Date();
  }

  userLink(user) {
    if (this.organizationContext) {
      return '/settings/users/' + user.id;
    } else {
      return '../../../users/' + user.id;
    }
  }

  private update(message) {
    this.groupService.update(this.domainId, this.group.id, this.group, this.organizationContext).subscribe((data) => {
      this.group = data;
      this.loadMembers();
      this.snackbarService.open(message);
    });
  }
}

@Component({
  selector: 'add-member',
  templateUrl: './add/add-member.component.html',
  standalone: false,
})
export class AddMemberComponent {
  @ViewChild('memberInput', { static: true }) memberInput: ElementRef<HTMLInputElement>;
  @ViewChild(MatAutocompleteTrigger, { static: true }) trigger;
  memberCtrl = new UntypedFormControl();
  filteredUsers: any[];
  selectedMembers: any[] = [];
  removable = true;
  addOnBlur = true;
  separatorKeysCodes: number[] = [ENTER, COMMA];
  private groupMembers: string[] = [];

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: any,
    public dialogRef: MatDialogRef<AddMemberComponent>,
    private userService: UserService,
  ) {
    this.groupMembers = data.groupMembers || [];
    this.memberCtrl.valueChanges
      .pipe(
        filter((searchTerm) => typeof searchTerm === 'string' || searchTerm instanceof String),
        switchMap((searchTerm) => this.userService.search(data.domain, 'q=' + searchTerm + '*', 0, 30, data.organizationContext)),
        tap((response) => {
          this.filteredUsers = response.data.filter(
            (domainUser) =>
              map(this.selectedMembers, 'id').indexOf(domainUser.id) === -1 && this.groupMembers.indexOf(domainUser.id) === -1,
          );
        }),
      )
      .subscribe();
  }

  onSelectionChanged(event) {
    this.selectedMembers.push(event.option.value);
    this.memberInput.nativeElement.value = '';
    this.memberCtrl.setValue(null);
  }

  remove(member: string): void {
    const index = this.selectedMembers.indexOf(member);

    if (index >= 0) {
      this.selectedMembers.splice(index, 1);
    }
  }

  displayName(user) {
    if (user.firstName) {
      return user.firstName + ' ' + (user.lastName ? user.lastName : '');
    } else {
      return user.username;
    }
  }
}
