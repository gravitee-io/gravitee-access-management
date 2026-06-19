{{- define "dataPlanes" }}
dataPlanes:
{{- if .Values.dataPlanes }}
{{- .Values.dataPlanes | toYaml | nindent 2 }}
{{- else }}
  - id: default
    name: Legacy domains
{{- include "repositoryBlock" (dict "context" . "scope" "gateway") }}
{{- end }}
{{- end }}