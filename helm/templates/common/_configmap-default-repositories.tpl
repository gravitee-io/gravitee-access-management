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
{{- end }}
{{- end }}