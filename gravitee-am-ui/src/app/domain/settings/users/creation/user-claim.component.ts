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
import { Component, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'user-claim-component',
  templateUrl: './user-claim.component.html',
  standalone: false,
})
export class UserClaimComponent {
  private claimId: string = Math.random().toString(36).substring(7);
  claim: any = {};
  @Output() addClaimChange = new EventEmitter();
  @Output() removeClaimChange = new EventEmitter();

  addClaim(event) {
    event.preventDefault();
    this.claim.id = this.claimId;
    this.addClaimChange.emit(this.claim);
  }

  removeClaim(claim, event) {
    event.preventDefault();
    this.removeClaimChange.emit(claim);
  }
}
