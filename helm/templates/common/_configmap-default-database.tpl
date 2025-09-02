{{ define "db.default.mongodb" }}
mongodb:
  {{- if .Values.mongo.dbhost}}
  host: {{ .Values.mongo.dbhost }}
  {{- end }}
  {{- if .Values.mongo.dbport }}
  port: {{ .Values.mongo.dbport }}
  {{- end }}
  sslEnabled: {{ .Values.mongo.sslEnabled }}
  {{- if .Values.mongo.keystore }}
  keystore:
    {{ toYaml .Values.mongo.keystore | nindent 4 }}
  {{- end }}
  {{- if .Values.mongo.truststore }}
  truststore:
    {{ toYaml .Values.mongo.truststore | nindent 4 }}
  {{- end }}
  socketKeepAlive: {{ .Values.mongo.socketKeepAlive }}
  {{- if .Values.mongo.uri }}
  uri: {{ .Values.mongo.uri }}
  {{- else if .Values.mongo.servers }}
  servers:
    {{- .Values.mongo.servers | nindent 2 }}
  dbname: {{ .Values.mongo.dbname }}
  {{- if (eq .Values.mongo.auth.enabled true) }}
  username: {{ .Values.mongo.auth.username }}
  {{- if .Values.mongo.auth.password }}
  password: {{ .Values.mongo.auth.password }}
  {{- end }}
  {{- if .Values.mongo.auth.source }}
  authSource: {{ .Values.mongo.auth.source }}
  {{- end }}
  {{- end }}
  {{- else }}
  uri: mongodb://{{- if (eq .Values.mongo.auth.enabled true) }}{{ .Values.mongo.auth.username }}:{{ .Values.mongo.auth.password }}@{{- end }}{{ .Values.mongo.dbhost }}:{{ .Values.mongo.dbport }}/{{ .Values.mongo.dbname }}?{{- if .Values.mongo.rsEnabled }}&replicaSet={{ .Values.mongo.rs }}{{- end }}{{- if (eq .Values.mongo.auth.enabled true) }}&authSource={{ .Values.mongo.auth.source }}{{- end }}{{- if .Values.mongo.connectTimeoutMS }}&connectTimeoutMS={{ .Values.mongo.connectTimeoutMS }}{{- end }}
  {{- end }}
{{- end }}

{{ define "db.default.jdbc" }}
jdbc:
  driver: {{ .Values.jdbc.driver }}
  host: {{ .Values.jdbc.host }}
  port: {{ .Values.jdbc.port }}
  database: {{ .Values.jdbc.database }}
  {{- if .Values.jdbc.username }}
  username: {{ .Values.jdbc.username }}
  {{- end }}
  {{- if .Values.jdbc.password }}
  password: {{ .Values.jdbc.password }}
  {{- end }}
  {{- if .Values.jdbc.schema }}
  schema: {{ .Values.jdbc.schema }}
  {{- end }}
  {{- if .Values.jdbc.pool }}
  {{ toYaml .Values.jdbc.pool | nindent 2 | trim -}}
  {{- end }}
  {{- if .Values.jdbc.sslEnabled }}
  sslEnabled: {{ .Values.jdbc.sslEnabled }}
  {{- end }}
  {{- if .Values.jdbc.sslMode }}
  sslMode: {{ .Values.jdbc.sslMode }}
  {{- end }}
  {{- if .Values.jdbc.sslServerCert }}
  sslServerCert: {{ .Values.jdbc.sslServerCert }}
  {{- end }}
  {{- if .Values.jdbc.trustServerCertificate }}
  trustServerCertificate: {{ .Values.jdbc.trustServerCertificate }}
  {{- end }}
  {{- if hasKey .Values.jdbc "disableSslHostnameVerification" }}
  disableSslHostnameVerification: {{ .Values.jdbc.disableSslHostnameVerification }}
  {{- end }}
  {{- if .Values.jdbc.trustStore }}
  trustStore:
    {{ toYaml .Values.jdbc.trustStore | nindent 4 | trim -}}
  {{- end }}
{{- end }}