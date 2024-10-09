{{- define "api.httpHeadersProbes" -}}
    {{- $httpHeadersProbes := dict }}
    {{- if and (not (empty .Values.api.http.services.core.http.authentication.password)) (not (eq .Values.api.http.services.core.http.authentication.type "none")) }}
      {{- $httpHeadersProbes = dict "httpHeaders" (list (dict
          "name" "Authorization"
          "value" (printf "Basic %s" (printf "admin:%s" .Values.api.http.services.core.http.authentication.password | b64enc))
      )) }}
    {{- end }}

    {{- $httpHeadersProbes | toYaml }}
{{- end }}

{{- define "api.computeLivenessProbe" -}}
    {{- $httpHeadersProbes := (include "api.httpHeadersProbes" . | fromYaml) -}}

    {{- $defaultLivenessProbe := dict
        "initialDelaySeconds" 30
        "periodSeconds" 30
        "timeoutSeconds" 3
        "successThreshold" 1
        "failureThreshold" 3
        "httpGet" (merge (dict
          "path" "/_node/health?probes=jetty-http-server"
          "scheme" "HTTP"
          "port" (.Values.api.http.services.core.http.port | default 18082)
        ) $httpHeadersProbes)
    }}

    {{- $computeLivenessProbe := (mergeOverwrite $defaultLivenessProbe .Values.api.livenessProbe) -}}
    {{- if hasKey $computeLivenessProbe "tcpSocket" }}
      {{- $_ := unset $computeLivenessProbe "httpGet" }}
    {{- end }}

    {{- $computeLivenessProbe | toYaml }}
{{- end }}

{{- define "api.computeReadinessProbe" -}}
    {{- $httpHeadersProbes := (include "api.httpHeadersProbes" . | fromYaml) -}}

    {{- $defaultReadinessProbe := dict
        "initialDelaySeconds" 30
        "periodSeconds" 30
        "timeoutSeconds" 3
        "successThreshold" 1
        "failureThreshold" 3
        "httpGet" (merge (dict
          "path" "/_node/health?probes=jetty-http-server"
          "scheme" "HTTP"
          "port" (.Values.api.http.services.core.http.port | default 18082)
        ) $httpHeadersProbes)
    }}

    {{- $computeReadinessProbe := (mergeOverwrite $defaultReadinessProbe .Values.api.readinessProbe) -}}
    {{- if hasKey $computeReadinessProbe "tcpSocket" }}
      {{- $_ := unset $computeReadinessProbe "httpGet" }}
    {{- end }}

    {{- $computeReadinessProbe | toYaml }}
{{- end }}

{{- define "api.computeStartupProbe" -}}
    {{- $httpHeadersProbes := (include "api.httpHeadersProbes" . | fromYaml) -}}

    {{- $defaultStartupProbe := dict
        "initialDelaySeconds" 10
        "periodSeconds" 10
        "timeoutSeconds" 1
        "successThreshold" 1
        "failureThreshold" 29
        "httpGet" (merge (dict
          "path" "/_node/health?probes=jetty-http-server,management-repository"
          "scheme" "HTTP"
          "port" (.Values.api.http.services.core.http.port | default 18082)
        ) $httpHeadersProbes)
    }}

    {{- $computeStartupProbe := (mergeOverwrite $defaultStartupProbe .Values.api.startupProbe) -}}
    {{- if hasKey $computeStartupProbe "tcpSocket" }}
      {{- $_ := unset $computeStartupProbe "httpGet" }}
    {{- end }}

    {{- $computeStartupProbe | toYaml }}
{{- end }}
