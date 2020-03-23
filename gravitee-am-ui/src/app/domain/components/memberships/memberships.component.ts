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
import {Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChange, SimpleChanges} from '@angular/core';
import {FormControl} from "@angular/forms";
import {OrganizationService} from "../../../services/organization.service";
import {DialogService} from "../../../services/dialog.service";
import * as _ from 'lodash';

@Component({
  selector: 'app-memberships',
  templateUrl: './memberships.component.html',
  styleUrls: ['./memberships.component.scss']
})
export class MembershipsComponent implements OnInit, OnChanges {
  @Input('roleType') roleType: any;
  @Input('members') members: any;
  @Input('createMode') createMode: boolean;
  @Input('editMode') editMode: boolean;
  @Input('deleteMode') deleteMode: boolean;
  @Output() userMembershipAdded = new EventEmitter<any>();
  @Output() groupMembershipAdded = new EventEmitter<any>();
  @Output() membershipDeleted = new EventEmitter<any>();
  @Output() membershipUpdated = new EventEmitter<any>();
  userMembers: any[] = [];
  groupMembers: any[] = [];
  groups: any[];
  roles: any[];
  userCtrl = new FormControl();
  filteredUsers: any[];
  filteredGroups: any[];
  selectedUser: any;
  selectedGroup: any;
  selectedUserRole: any;
  selectedGroupRole: any;
  displayReset = false;

  constructor(private organizationService: OrganizationService,
              private dialogService: DialogService) {
    this.userCtrl.valueChanges
      .subscribe(searchTerm => {
        if (searchTerm && typeof searchTerm === 'string') {
          this.organizationService.searchUsers(searchTerm + '*', 0, 30).subscribe(response => {
            this.filteredUsers = response.data.filter(user => _.map(this.userMembers, 'memberId').indexOf(user.id) === -1);
          });
        }
      });
  }

  ngOnInit() {
    this.initMembers();
    this.loadRoles();
    this.loadGroups();
  }

  ngOnChanges(changes: SimpleChanges) {
    const members = changes.members;
    if (members.currentValue) {
      this.initMembers();
    }
  }

  onUserSelectionChanged(event) {
    this.selectedUser = event.option.value['id'];
    this.displayReset = true;
  }

  displayUserFn(user?: any): string | undefined {
    return user ? user.username : undefined;
  }

  addUserMembership(event) {
    event.preventDefault();
    const membership = {};
    membership['memberId'] = this.selectedUser;
    membership['role'] = this.selectedUserRole;
    this.userMembershipAdded.emit(membership);
    this.selectedUser = null;
    this.selectedUserRole = null;
    this.userCtrl.reset();
    this.filteredUsers = [];
  }

  addGroupMembership(event) {
    event.preventDefault();
    const membership = {};
    membership['memberId'] = this.selectedGroup;
    membership['role'] = this.selectedGroupRole;
    this.groupMembershipAdded.emit(membership);
    this.selectedGroup = null;
    this.selectedGroupRole = null;
  }

  avatarUrl(user) {
    return 'assets/material-letter-icons/' + user.name.charAt(0).toUpperCase() + '.svg';
  }

  delete(membershipId, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete member', 'Are you sure you want to delete this member ?')
      .subscribe(res => {
        if (res) {
          this.membershipDeleted.emit(membershipId);
        }
      });
  }

  update(memberId, memberType, event) {
    this.dialogService
      .confirm('Updater member', 'Are you sure you want to change this membership ?')
      .subscribe(res => {
        if (res) {
          const member = {};
          member['memberId'] = memberId;
          member['memberType'] = memberType;
          member['role'] = event.value;
          this.membershipUpdated.emit(member);
        }
      });
  }

  isPrimaryOwner(membership) {
    return membership.roleName && membership.roleName.endsWith('_PRIMARY_OWNER');
  }

  private initMembers() {
    const memberships = this.members.memberships;
    const metadata = this.members.metadata;
    this.userMembers = _.map(_.filter(memberships, {memberType: 'user'}), m => {
      m.name = (metadata['users'][m.memberId]) ? metadata['users'][m.memberId].displayName : 'Unknown user';
      m.roleName = (metadata['roles'][m.roleId]) ? metadata['roles'][m.roleId].name : 'Unknown role';
      return m;
    });
    this.groupMembers = _.map(_.filter(memberships, {memberType: 'group'}), m => {
      m.name = (metadata['groups'][m.memberId]) ? metadata['groups'][m.memberId].displayName : 'Unknown group';
      m.roleName = (metadata['roles'][m.roleId]) ? metadata['roles'][m.roleId].name : 'Unknown role';
      return m;
    });
    if (this.groups) {
      this.filterGroups();
    }
  }

  private loadRoles() {
    this.organizationService.roles(this.roleType).subscribe(response => {
      this.roles = response;
    });
  }

  private loadGroups() {
    this.organizationService.groups().subscribe(response => {
      this.groups = response.data;
      this.filterGroups();
    });
  }

  private filterGroups() {
    this.filteredGroups = this.groups.filter(group => _.map(this.groupMembers, 'memberId').indexOf(group.id) === -1);
  }
}
