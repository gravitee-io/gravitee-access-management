#
# Copyright (C) 2015 The Gravitee team (http://gravitee.io)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

curl --retry 2 --retry-delay 5 --max-time 30 --retry-all-errors https://jdbc.postgresql.org/download/postgresql-42.6.0.jar -o dev/plugins/jdbc/postgresql-jdbc.jar || \
curl --retry 4 --retry-delay 10 --retry-all-errors https://repo1.maven.org/maven2/org/postgresql/postgresql/42.6.0/postgresql-42.6.0.jar -o dev/plugins/jdbc/postgresql-jdbc.jar
curl --retry 4 --retry-delay 10 --retry-all-errors https://repo1.maven.org/maven2/org/postgresql/r2dbc-postgresql/1.0.2.RELEASE/r2dbc-postgresql-1.0.2.RELEASE.jar -o dev/plugins/jdbc/r2dbc-postgresql.jar
