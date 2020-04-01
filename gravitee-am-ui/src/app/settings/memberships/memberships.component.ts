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
import {ActivatedRoute} from "@angular/router";
import {OrganizationService} from "../../services/organization.service";
import {SnackbarService} from "../../services/snackbar.service";
import {DialogService} from "../../services/dialog.service";
import {AuthService} from "../../services/auth.service";

@Component({
  selector: 'app-domain-settings-memberships',
  templateUrl: './memberships.component.html',
  styleUrls: ['./memberships.component.scss']
})
export class SettingsMembershipsComponent implements OnInit {
  roleType = 'ORGANIZATION';
  members: any;
  createMode = false;
  editMode = false;
  deleteMode = false;

  constructor(private organizationService: OrganizationService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private authService: AuthService,
              private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.members = this.route.snapshot.data['members'];
    this.createMode = this.authService.hasPermissions(['organization_member_create']);
    this.editMode = this.authService.hasPermissions(['organization_member_update']);
    this.deleteMode = this.authService.hasPermissions(['organization_member_delete']);
  }

  add(membership) {
    this.organizationService.addMember(membership.memberId, membership.memberType, membership.role).subscribe(response => {
      this.reloadMembers();
      this.snackbarService.open('Member added');
    });

  }

  delete(membershipId) {
      this.organizationService.removeMember(membershipId).subscribe(response => {
        this.snackbarService.open('Member deleted');
        this.reloadMembers();
      });
  }

  update(member) {
    this.organizationService.addMember(member.memberId, member.memberType, member.role).subscribe(response => {
      this.snackbarService.open('Member updated');
      this.reloadMembers();
    });
  }

  private reloadMembers() {
    this.organizationService.members().subscribe(response => {
      this.members = response;
    })
  }
}
