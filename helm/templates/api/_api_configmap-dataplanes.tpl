{{- define "dataPlanes" }}
{{- $computeRepositories := (include "repositories" . | fromYaml) -}}

dataPlanes:
{{- if .Values.dataPlanes }}
{{- .Values.dataPlanes | toYaml | nindent 2 }}
{{- else }}
  - id: default
    name: Legacy domains
{{- $computeRepositories.repositories.gateway | toYaml | nindent 4 }}
{{- end }}
{{- end }}