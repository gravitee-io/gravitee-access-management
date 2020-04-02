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
import { Component, OnInit, Input } from '@angular/core';

@Component({
  selector: 'app-user-avatar',
  templateUrl: './user-avatar.component.html'
})
export class UserAvatarComponent implements OnInit {
  @Input() user: any;
  @Input() width: string;
  url: string;

  constructor() { }

  ngOnInit() {
    if (this.user.additionalInformation && this.user.additionalInformation['picture']) {
      this.url = this.user.additionalInformation['picture'];
    }
    if (this.user.username) {
      this.url = 'assets/material-letter-icons/' + this.user.username.charAt(0).toUpperCase() + '.png';
    } else {
      this.url = 'assets/material-letter-icons/' + this.user.name.charAt(0).toUpperCase() + '.png';
    }
  }

}
