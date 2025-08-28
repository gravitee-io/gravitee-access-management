<@compress single_line=true>
{
  "_type": "audit",
  "@timestamp":"${@timestamp}",
  "date" : "${date}",
  "event_id" : "${audit.getId()}",
  "event_type":"${audit.getType()}",
  "organizationId":"${audit.getOrganizationId()}",
  "environmentId":"${audit.getEnvironmentId()}",
  "transactionId":"${audit.getTransactionId()}",
  <#if audit.getNodeId()??>
    "nodeId":"${audit.getNodeId()?j_string}",
  </#if>
  <#if audit.getNodeHostname()??>
    "nodeHostname":"${audit.getNodeHostname()?j_string}",
  </#if>
  <#if audit.getReferenceType()??>
    "referenceType":"${audit.getReferenceType()?j_string}",
  </#if>
  "referenceId":"${audit.getReferenceId()}",
  "status":"${audit.getStatus()}"
  <#if audit.getOutcome()??>
    ,"outcome": {
      <#if audit.getOutcome().getStatus()??> "status":"${audit.getOutcome().getStatus()}"</#if>
      <#if audit.getOutcome().getMessage()??>, "message":"${audit.getOutcome().getMessage()}"</#if> 
    }
  </#if>
  <#if audit.getAccessPoint()??>
    ,"accessPoint": {
      "id":  <#if audit.getAccessPoint().getId()??> "${audit.getAccessPoint().getId()}" <#else> null </#if>
      <#if audit.getAccessPoint().getAlternativeId()??>, "alternativeId":"${audit.getAccessPoint().getAlternativeId()}"</#if>
      <#if audit.getAccessPoint().getDisplayName()??>, "displayName":"${audit.getAccessPoint().getDisplayName()}"</#if>
      <#if audit.getAccessPoint().getIpAddress()??>, "ipAddress":"${audit.getAccessPoint().getIpAddress()}"</#if>
      <#if audit.getAccessPoint().getUserAgent()??>, "userAgent":"${audit.getAccessPoint().getUserAgent()}"</#if>
    }
  </#if>
  <#if audit.getActor()??>
    ,"actor": {
      "id":  <#if audit.getActor().getId()??> "${audit.getActor().getId()}" <#else> null </#if>
      <#if audit.getActor().getAlternativeId()??>, "alternativeId":"${audit.getActor().getAlternativeId()}"</#if>
      <#if audit.getActor().getType()??>, "type":"${audit.getActor().getType()}"</#if>
      <#if audit.getActor().getDisplayName()??>, "displayName":"${audit.getActor().getDisplayName()}"</#if>
      <#if audit.getActor().getReferenceType()??>
        , "referenceType":"${audit.getActor().getReferenceType()?j_string}"
      </#if>
      <#if audit.getActor().getReferenceId()??>, "referenceId":"${audit.getActor().getReferenceId()}"</#if>
    }
  </#if>
  <#if audit.getTarget()??>
    ,"target": {
      "id":  <#if audit.getTarget().getId()??> "${audit.getTarget().getId()}" <#else> null </#if>
      <#if audit.getTarget().getAlternativeId()??>, "alternativeId":"${audit.getTarget().getAlternativeId()}"</#if>
      <#if audit.getTarget().getType()??>, "type":"${audit.getTarget().getType()}"</#if>
      <#if audit.getTarget().getDisplayName()??>, "displayName":"${audit.getTarget().getDisplayName()}"</#if>
      <#if audit.getTarget().getReferenceType()??>
        , "referenceType":"${audit.getTarget().getReferenceType()?j_string}"
      </#if>
      <#if audit.getTarget().getReferenceId()??>, "referenceId":"${audit.getTarget().getReferenceId()}"</#if>
    }
  </#if>
}</@compress>