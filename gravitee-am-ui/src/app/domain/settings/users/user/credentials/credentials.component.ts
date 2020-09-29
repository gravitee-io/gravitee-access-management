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
import { ActivatedRoute, Router } from '@angular/router';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { DialogService } from '../../../../../services/dialog.service';
import { UserService } from '../../../../../services/user.service';
import { AuthService } from '../../../../../services/auth.service';

@Component({
  selector: 'app-user-credentials',
  templateUrl: './credentials.component.html',
  styleUrls: ['./credentials.component.scss']
})
export class UserCredentialsComponent implements OnInit {
  private domainId: string;
  private user: any;
  private credentials: any[];
  canRevoke: boolean;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private userService: UserService,
              private authService: AuthService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.user = this.route.snapshot.data['user'];
    this.credentials = this.route.snapshot.data['credentials'];
    this.canRevoke = this.authService.hasPermissions(['domain_user_update']);
  }

  get isEmpty() {
    return !this.credentials || this.credentials.length === 0;
  }

  remove(event, credential) {
    event.preventDefault();
    this.dialogService
      .confirm('Remove WebAuthn credential', 'Are you sure you want to remove this credential ?')
      .subscribe(res => {
        if (res) {
          this.userService.removeCredential(this.domainId, this.user.id, credential.id).subscribe(response => {
            this.snackbarService.open('Credential deleted');
            this.loadCredentials();
          });
        }
      });
  }

  loadCredentials() {
    this.userService.credentials(this.domainId, this.user.id).subscribe(credentials => {
      this.credentials = credentials;
    });
  }
}
