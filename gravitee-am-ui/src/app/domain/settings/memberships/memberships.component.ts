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
import {DomainService} from "../../../services/domain.service";
import {ActivatedRoute} from "@angular/router";
import {SnackbarService} from "../../../services/snackbar.service";
import {DialogService} from "../../../services/dialog.service";

@Component({
  selector: 'app-domain-settings-memberships',
  templateUrl: './memberships.component.html',
  styleUrls: ['./memberships.component.scss']
})
export class DomainSettingsMembershipsComponent implements OnInit {
  private domainId: string;
  domainRoleScope = 'DOMAIN';
  members: any;

  constructor(private domainService: DomainService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.members = this.route.snapshot.data['members'];
  }

  addUserMembership(membership) {
    this.domainService.addMember(this.domainId, membership.memberId, 'USER', membership.role).subscribe(response => {
      this.reloadMembers();
      this.snackbarService.open('Member added');
    });

  }

  addGroupMembership(membership) {
    event.preventDefault();
    this.domainService.addMember(this.domainId, membership.memberId, 'GROUP', membership.role).subscribe(response => {
      this.reloadMembers();
      this.snackbarService.open('Member added');
    });
  }

  delete(membershipId) {
      this.domainService.removeMember(this.domainId, membershipId).subscribe(response => {
        this.snackbarService.open('Member deleted');
        this.reloadMembers();
      });
  }

  update(member) {
    this.domainService.addMember(this.domainId, member.memberId, member.memberType, member.role).subscribe(response => {
      this.snackbarService.open('Member updated');
      this.reloadMembers();
    });
  }

  private reloadMembers() {
    this.domainService.members(this.domainId).subscribe(response => {
      this.members = response;
    })
  }
}
