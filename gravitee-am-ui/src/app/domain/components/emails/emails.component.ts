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

@Component({
  selector: 'app-emails',
  templateUrl: './emails.component.html',
  styleUrls: ['./emails.component.scss']
})
export class EmailsComponent implements OnInit {
  emails: any[];
  domainId: string;

  constructor() { }


  ngOnInit() {
    this.emails = this.getEmails();
  }

  getEmails() {
    return [
      {
        'name': 'Registration confirmation',
        'description': 'Registration email to confirm user account',
        'template': 'REGISTRATION_CONFIRMATION'
      },
      {
        'name': 'Reset password',
        'description': 'Reset password email to ask for a new one',
        'template': 'RESET_PASSWORD'
      }
    ]
  }
}
