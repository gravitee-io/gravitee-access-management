{{- define "gateway.httpHeadersProbes" -}}
    {{- $httpHeadersProbes := dict }}
    {{- if and
        .Values.gateway.services.core.http.enabled
        (eq (default "basic" .Values.gateway.services.core.http.authentication.type) "basic")
        .Values.gateway.services.core.http.authentication.password
    }}
      {{- $httpHeadersProbes = dict "httpHeaders" (list (dict
          "name" "Authorization"
          "value" (printf "Basic %s" (printf "admin:%s" .Values.gateway.services.core.http.authentication.password | b64enc))
      )) }}
    {{- end }}

    {{- $httpHeadersProbes | toYaml }}
{{- end }}

{{- define "gateway.gatewayServiceCoreScheme" -}}
    {{- $gatewayServiceCoreScheme := ( ternary "HTTPS" "HTTP" .Values.gateway.services.core.http.secured) }}
    {{- $gatewayServiceCoreScheme }}
{{- end }}

{{- define "gateway.computeLivenessProbe" -}}
    {{- $httpHeadersProbes := (include "gateway.httpHeadersProbes" . | fromYaml) -}}
    {{- $gatewayServiceCoreScheme := (include "gateway.gatewayServiceCoreScheme" .) -}}

    {{- $defaultLivenessProbe := dict
        "initialDelaySeconds" 30
        "periodSeconds" 30
        "timeoutSeconds" 3
        "successThreshold" 1
        "failureThreshold" 3
        "httpGet" (merge (dict
          "path" "/_node/health?probes=http-server"
          "scheme" $gatewayServiceCoreScheme
          "port" (.Values.gateway.services.core.http.port | default 18082)
        ) $httpHeadersProbes)
    }}

    {{- $computeLivenessProbe := (mergeOverwrite $defaultLivenessProbe .Values.gateway.livenessProbe) -}}
    {{- if or
            (hasKey $computeLivenessProbe "tcpSocket")
            (hasKey $computeLivenessProbe "exec")
    }}
      {{- $_ := unset $computeLivenessProbe "httpGet" }}
    {{- end }}

    {{- $computeLivenessProbe | toYaml }}
{{- end }}

{{- define "gateway.computeReadinessProbe" -}}
    {{- $httpHeadersProbes := (include "gateway.httpHeadersProbes" . | fromYaml) -}}
    {{- $gatewayServiceCoreScheme := (include "gateway.gatewayServiceCoreScheme" .) -}}
    {{- $httpGetPath := (ternary "/_node/health?probes=security-domain-sync" "/_node/health?probes=http-server" (eq .Values.gateway.readinessProbe.domainSync true)) -}}

    {{- $defaultReadinessProbe := dict
        "initialDelaySeconds" 30
        "periodSeconds" 30
        "timeoutSeconds" 3
        "successThreshold" 1
        "failureThreshold" 3
        "httpGet" (merge (dict
          "path" $httpGetPath
          "scheme" $gatewayServiceCoreScheme
          "port" (.Values.gateway.services.core.http.port | default 18082)
        ) $httpHeadersProbes)
    }}

    {{- $computeReadinessProbe := (mergeOverwrite $defaultReadinessProbe .Values.gateway.readinessProbe) -}}
    {{- if or
            (hasKey $computeReadinessProbe "tcpSocket")
            (hasKey $computeReadinessProbe "exec")
    }}
      {{- $_ := unset $computeReadinessProbe "httpGet" }}
    {{- end }}

    {{- $_ := unset $computeReadinessProbe "domainSync" }}

    {{- $computeReadinessProbe | toYaml }}
{{- end }}

{{- define "gateway.computeStartupProbe" -}}
    {{- $httpHeadersProbes := (include "gateway.httpHeadersProbes" . | fromYaml) -}}
    {{- $gatewayServiceCoreScheme := (include "gateway.gatewayServiceCoreScheme" .) -}}

    {{- $defaultStartupProbe := dict
        "initialDelaySeconds" 10
        "periodSeconds" 10
        "timeoutSeconds" 1
        "successThreshold" 1
        "failureThreshold" 29
        "httpGet" (merge (dict
          "path" "/_node/health?probes=http-server,security-domain-sync"
          "scheme" $gatewayServiceCoreScheme
          "port" (.Values.gateway.services.core.http.port | default 18082)
        ) $httpHeadersProbes)
    }}

    {{- $computeStartupProbe := (mergeOverwrite $defaultStartupProbe .Values.gateway.startupProbe) -}}
    {{- if or
            (hasKey $computeStartupProbe "tcpSocket")
            (hasKey $computeStartupProbe "exec")
    }}
      {{- $_ := unset $computeStartupProbe "httpGet" }}
    {{- end }}

    {{- $computeStartupProbe | toYaml }}
{{- end }}
