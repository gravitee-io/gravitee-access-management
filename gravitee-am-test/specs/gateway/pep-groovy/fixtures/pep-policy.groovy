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
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.gravitee.policy.groovy.PolicyResult.State

def permits = {{PERMITS}}
def inspect = {{INSPECT}}

def params = request.parameters
if (params.getFirst('requested_token_type') != 'urn:ietf:params:oauth:token-type:id-jag') {
    return
}

def deny = { String key, String reason ->
    result.state = State.FAILURE
    result.code = 403
    result.key = key
    result.error = reason
}

def subjectOf = { String token ->
    token ? new JsonSlurper().parseText(new String(token.tokenize('.')[1].decodeBase64Url(), 'UTF-8')).sub : null
}

def clientIdOf = { String authorization ->
    authorization?.startsWith('Basic ')
        ? URLDecoder.decode(new String(authorization.substring(6).decodeBase64(), 'UTF-8').tokenize(':')[0], 'UTF-8')
        : params.getFirst('client_id')
}

def user = subjectOf(params.getFirst('subject_token'))
def client = clientIdOf(request.headers().get('Authorization'))
def audience = params.getFirst('audience')

def missing = [subject_token: user, client_id: client, audience: audience].findAll { it.value == null }.keySet()
if (missing) {
    deny('PEP_DENIED', 'missing: ' + missing.join(', '))
    return
}

def evaluation = { String type, String id ->
    [
        subject : [type: type, id: id],
        action  : [name: 'issue:id_jag:token_exchange'],
        resource: [type: 'audience', id: audience]
    ]
}

def evaluationRequest = [
    context    : [client_id: client],
    evaluations: [evaluation('user', user), evaluation('client', client)],
    options    : [evaluations_semantic: 'execute_all']
]

if (inspect) {
    deny('PEP_INSPECT', JsonOutput.toJson(evaluationRequest))
    return
}

def decide = { Map e ->
    permits.any { it.subject == e.subject.id && it.action == e.action.name && it.resource == e.resource.id }
}

def refused = evaluationRequest.evaluations.findAll { !decide(it) }.collect { it.subject.type }
if (refused) {
    deny('PEP_DENIED', 'denied: ' + refused.join(', '))
}
