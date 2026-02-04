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
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { filter, switchMap, tap } from 'rxjs/operators';
import { map } from 'lodash';

import { OrganizationService } from '../../services/organization.service';
import { DialogService } from '../../services/dialog.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-memberships',
  templateUrl: './memberships.component.html',
  styleUrls: ['./memberships.component.scss'],
  standalone: false,
})
export class MembershipsComponent implements OnInit, OnChanges {
  @Input() roleType: any;
  // eslint-disable-next-line @angular-eslint/no-input-rename
  @Input('members') resourceMembers: any;
  @Input() createMode: boolean;
  @Input() editMode: boolean;
  @Input() deleteMode: boolean;
  @Output() membershipAdded = new EventEmitter<any>();
  @Output() membershipDeleted = new EventEmitter<any>();
  @Output() membershipUpdated = new EventEmitter<any>();
  dataLoaded: boolean;
  members: any[];
  selectedMemberType = 'user';
  selectedMember: any;
  selectedRole: any;
  groups: any[];
  roles: any[];
  userCtrl = new UntypedFormControl();
  filteredUsers: any[];
  filteredGroups: any[];
  displayReset = false;

  constructor(
    private organizationService: OrganizationService,
    private dialogService: DialogService,
    private authService: AuthService,
  ) {
    this.userCtrl.valueChanges
      .pipe(
        filter((searchTerm) => searchTerm && typeof searchTerm === 'string'),
        switchMap((searchTerm) => this.organizationService.searchUsers('q=' + searchTerm + '*', 0, 30)),
        tap((response) => {
          this.filteredUsers = response.data.filter((user) => map(this.members, 'memberId').indexOf(user.id) === -1);
        }),
      )
      .subscribe();
  }

  ngOnInit() {
    this.loadRoles();
    this.loadGroups();
    setTimeout(() => {
      this.dataLoaded = true;
    }, 0);
  }

  ngOnChanges(changes: SimpleChanges) {
    const members = changes.resourceMembers;
    if (members?.currentValue) {
      this.members = members.currentValue;
    }
  }

  onUserSelectionChanged(event) {
    this.selectedMember = event.option.value['id'];
    this.displayReset = true;
  }

  displayUserFn(user?: any): string | undefined {
    return user ? user.username : undefined;
  }

  addMembership(event) {
    event.preventDefault();
    const membership = {};
    membership['memberId'] = this.selectedMember;
    membership['role'] = this.selectedRole;
    membership['memberType'] = this.selectedMemberType.toUpperCase();
    this.membershipAdded.emit(membership);
    this.selectedMember = null;
    this.selectedRole = null;
    this.userCtrl.reset();
    this.filteredUsers = [];
    this.filterGroups();
  }

  delete(membershipId, event) {
    event.preventDefault();
    this.dialogService.confirm('Delete member', 'Are you sure you want to delete this member ?').subscribe((res) => {
      if (res) {
        this.membershipDeleted.emit(membershipId);
        this.filterGroups();
      }
    });
  }

  update(memberId, memberType, event) {
    this.dialogService.confirm('Updater member', 'Are you sure you want to change this membership ?').subscribe((res) => {
      if (res) {
        const member = {};
        member['memberId'] = memberId;
        member['memberType'] = memberType;
        member['role'] = event.value;
        this.membershipUpdated.emit(member);
        this.filterGroups();
      }
    });
  }

  isEditable(membership) {
    return !this.isPrimaryOwner(membership) && !this.isMySelf(membership);
  }

  isPrimaryOwner(membership) {
    return membership?.roleName.endsWith('_PRIMARY_OWNER');
  }

  isRoleDisabled(role) {
    return role.name.endsWith('_PRIMARY_OWNER') && role.system === true;
  }

  isMySelf(membership) {
    return this.authService.user().sub === membership.memberId && membership.memberType === 'user';
  }

  private loadRoles() {
    this.organizationService.roles(this.roleType).subscribe((response) => {
      this.roles = response;
    });
  }

  private loadGroups() {
    this.organizationService.groups().subscribe((response) => {
      this.groups = response.data;
      this.filterGroups();
    });
  }

  private filterGroups() {
    this.filteredGroups = this.groups.filter((group) => map(this.members, 'memberId').indexOf(group.id) === -1);
  }
}
