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
  selector: 'app-user-factors',
  templateUrl: './factors.component.html',
  styleUrls: ['./factors.component.scss']
})
export class UserFactorsComponent implements OnInit {
  private domainId: string;
  private user: any;
  private factors: any[];
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
    this.factors = this.route.snapshot.data['factors'];
    this.canRevoke = this.authService.hasPermissions(['domain_user_update']);
  }

  get isEmpty() {
    return !this.factors || this.factors.length === 0;
  }

  remove(event, factor) {
    event.preventDefault();
    this.dialogService
      .confirm('Remove MFA method', 'Are you sure you want to remove this multi-factor auth ?')
      .subscribe(res => {
        if (res) {
          this.userService.removeFactor(this.domainId, this.user.id, factor.id).subscribe(response => {
            this.snackbarService.open('Multi-factor authentication method ' + factor.name + ' deleted');
            this.loadFactors();
          });
        }
      });
  }

  loadFactors() {
    this.userService.factors(this.domainId, this.user.id).subscribe(factors => {
      this.factors = factors;
    });
  }
}
