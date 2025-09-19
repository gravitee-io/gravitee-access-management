{{- define "repositories" -}}
{{- $defaultMongo := (include "db.default.mongodb" . | fromYaml) -}}
{{- $defaultJdbc := (include "db.default.jdbc" . | fromYaml) -}}
repositories:
{{- if .Values.repositories }}
{{- .Values.repositories | toYaml | nindent 2 }}
{{- else }}
  management:
{{- if or (eq .Values.management.type "mongodb") (kindIs "invalid" .Values.management.type) }}
    type: mongodb
{{- $defaultMongo | toYaml | nindent 4 }}
{{- else if (eq .Values.management.type "jdbc") }}
    type: jdbc
{{- $defaultJdbc | toYaml | nindent 4 }}
{{- end }}
  oauth2:
{{- if or (eq .Values.oauth2.type "mongodb") (kindIs "invalid" .Values.oauth2.type) }}
    type: mongodb
{{- $defaultMongo | toYaml | nindent 4 }}
{{- else if (eq .Values.oauth2.type "jdbc") }}
    type: jdbc
{{- $defaultJdbc | toYaml | nindent 4 }}
{{- end }}
  gateway:
{{- if or (eq .Values.gateway.type "mongodb") (kindIs "invalid" .Values.gateway.type) }}
    type: mongodb
{{- $defaultMongo | toYaml | nindent 4 }}
{{- else if (eq .Values.gateway.type "jdbc") }}
    type: jdbc
{{- $defaultJdbc | toYaml | nindent 4 }}
{{- end }}
  ratelimit:
{{- if or (not .Values.repositories) (not .Values.repositories.ratelimit) (not .Values.repositories.ratelimit.type) -}}
{{- /* If repositories section is missing we initialize the ratelimit bloc using the legacy management block */ -}}
{{- if or (eq .Values.management.type "mongodb") (kindIs "invalid" .Values.management.type) }}
    type: mongodb
{{- $defaultMongo | toYaml | nindent 4 }}
{{- else if (eq .Values.management.type "jdbc") }}
    type: jdbc
{{- $defaultJdbc | toYaml | nindent 4 }}
{{- end }}
{{- else if or (not .Values.repositories.ratelimit) (not .Values.repositories.ratelimit.type) -}}
{{- /* If repositories.ratelimit section is missing we initialize the ratelimit bloc using the repository.management block */ -}}
{{- if or (eq .Values.repositories.management.type "mongodb") (kindIs "invalid" .Values.repositories.management.type) }}
    type: mongodb
{{- $defaultMongo | toYaml | nindent 4 }}
{{- else if (eq .Values.management.type "jdbc") }}
    type: jdbc
{{- $defaultJdbc | toYaml | nindent 4 }}
{{- end }}
{{- /* else we are using the repositories.ratelimit bloc */ -}}
{{- else if (eq .Values.repositories.ratelimit.type "mongodb") -}}
    type: mongodb
{{- $defaultMongo | toYaml | nindent 4 }}
{{- else if (eq .Values.repositories.ratelimit.type "jdbc") -}}
    type: jdbc
{{- $defaultJdbc | toYaml | nindent 4 }}
{{- end -}}

{{- end }}
{{- end }}