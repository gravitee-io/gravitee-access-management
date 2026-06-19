{{- /*
  Renders the connection settings for a single repository scope (management, oauth2, gateway, ratelimit).
*/ -}}
{{- define "repositoryBlock" -}}
{{- $root := .context -}}
{{- $scope := .scope -}}
{{- $defaultMongo := (include "db.default.mongodb" $root | fromYaml) -}}
{{- $defaultJdbc := (include "db.default.jdbc" $root | fromYaml) -}}
{{- if eq $scope "management" -}}
{{- if or (eq $root.Values.management.type "mongodb") (kindIs "invalid" $root.Values.management.type) }}
    type: mongodb
{{- $defaultMongo | toYaml | nindent 4 }}
{{- else if (eq $root.Values.management.type "jdbc") }}
    type: jdbc
{{- $defaultJdbc | toYaml | nindent 4 }}
{{- end }}
{{- else if eq $scope "oauth2" -}}
{{- if or (eq $root.Values.oauth2.type "mongodb") (kindIs "invalid" $root.Values.oauth2.type) }}
    type: mongodb
{{- $defaultMongo | toYaml | nindent 4 }}
{{- else if (eq $root.Values.oauth2.type "jdbc") }}
    type: jdbc
{{- $defaultJdbc | toYaml | nindent 4 }}
{{- end }}
{{- else if eq $scope "gateway" -}}
{{- if or (eq $root.Values.gateway.type "mongodb") (kindIs "invalid" $root.Values.gateway.type) }}
    type: mongodb
{{- $defaultMongo | toYaml | nindent 4 }}
{{- else if (eq $root.Values.gateway.type "jdbc") }}
    type: jdbc
{{- $defaultJdbc | toYaml | nindent 4 }}
{{- end }}
{{- else if eq $scope "ratelimit" -}}
{{- if not $root.Values.repositories -}}
{{- if or (eq $root.Values.management.type "mongodb") (kindIs "invalid" $root.Values.management.type) }}
    type: mongodb
{{- $defaultMongo | toYaml | nindent 4 }}
{{- else if (eq $root.Values.management.type "jdbc") }}
    type: jdbc
{{- $defaultJdbc | toYaml | nindent 4 }}
{{- end }}
{{- else if or (not $root.Values.repositories.ratelimit) (not $root.Values.repositories.ratelimit.type) -}}
{{- if or (eq $root.Values.repositories.management.type "mongodb") (kindIs "invalid" $root.Values.repositories.management.type) }}
    type: mongodb
{{- $defaultMongo | toYaml | nindent 4 }}
{{- else if (eq $root.Values.repositories.management.type "jdbc") }}
    type: jdbc
{{- $defaultJdbc | toYaml | nindent 4 }}
{{- end }}
{{- else if (eq $root.Values.repositories.ratelimit.type "mongodb") -}}
    type: mongodb
{{- $defaultMongo | toYaml | nindent 4 }}
{{- else if (eq $root.Values.repositories.ratelimit.type "jdbc") -}}
    type: jdbc
{{- $defaultJdbc | toYaml | nindent 4 }}
{{- end -}}
{{- end -}}
{{- end -}}

{{- /*
  Renders the repositories section.
  scope "management" (Management API): management repository only.
  scope "gateway" (Gateway, default): management, oauth2, gateway and ratelimit repositories.
  Pass as: include "repositories" (dict "context" . "scope" "management")
*/ -}}
{{- define "repositories" -}}
{{- $root := .context | default . -}}
{{- $scope := .scope | default "gateway" -}}
repositories:
{{- if $root.Values.repositories }}
{{- if eq $scope "management" }}
{{- if $root.Values.repositories.management }}
  management:
{{ $root.Values.repositories.management | toYaml | nindent 4 }}
{{- else }}
{{- $root.Values.repositories | toYaml | nindent 2 }}
{{- end }}
{{- else }}
{{- $root.Values.repositories | toYaml | nindent 2 }}
{{- end }}
{{- else if eq $scope "management" }}
  management:
{{- include "repositoryBlock" (dict "context" $root "scope" "management") }}
{{- else }}
  management:
{{- include "repositoryBlock" (dict "context" $root "scope" "management") }}
  oauth2:
{{- include "repositoryBlock" (dict "context" $root "scope" "oauth2") }}
  gateway:
{{- include "repositoryBlock" (dict "context" $root "scope" "gateway") }}
  ratelimit:
{{- include "repositoryBlock" (dict "context" $root "scope" "ratelimit") }}
{{- end }}
{{- end }}
