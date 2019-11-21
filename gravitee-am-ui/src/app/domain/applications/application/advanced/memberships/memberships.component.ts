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
import {ApplicationService} from "../../../../../services/application.service";
import {DialogService} from "../../../../../services/dialog.service";
import {SnackbarService} from "../../../../../services/snackbar.service";

@Component({
  selector: 'app-application-memberships',
  templateUrl: './memberships.component.html',
  styleUrls: ['./memberships.component.scss']
})
export class ApplicationMembershipsComponent implements OnInit {
  private domainId: string;
  private application: any;
  applicationRoleScope = 'APPLICATION';
  members: any;

  constructor(private applicationService: ApplicationService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.parent.params['domainId'];
    this.application = this.route.snapshot.parent.parent.data['application'];
    this.members = this.route.snapshot.data['members'];
  }


  addUserMembership(membership) {
    this.applicationService.addMember(this.domainId, this.application.id, membership.memberId, 'USER', membership.role).subscribe(response => {
      this.reloadMembers();
      this.snackbarService.open('Member added');
    });

  }

  addGroupMembership(membership) {
    this.applicationService.addMember(this.domainId, this.application.id, membership.memberId, 'GROUP', membership.role).subscribe(response => {
      this.reloadMembers();
      this.snackbarService.open('Member added');
    });
  }

  delete(membershipId) {
    this.applicationService.removeMember(this.domainId, this.application.id, membershipId).subscribe(response => {
      this.snackbarService.open('Member deleted');
      this.reloadMembers();
    });
  }

  update(member) {
    this.applicationService.addMember(this.domainId, this.application.id, member.memberId, member.memberType, member.role).subscribe(response => {
      this.snackbarService.open('Member updated');
      this.reloadMembers();
    });
  }


  private reloadMembers() {
    this.applicationService.members(this.domainId, this.application.id).subscribe(response => {
      this.members = response;
    })
  }
}
