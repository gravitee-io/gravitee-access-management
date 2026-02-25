permit(
  principal,
  action,
  resource is Document
) when {
  resource.owner == principal
};

permit(
  principal,
  action == Action::"read",
  resource is Document
) when {
  principal in Team::"engineering"
};

permit(
  principal,
  action in [Action::"read", Action::"write"],
  resource is Document
) when {
  principal.role == "manager"
};

permit(
  principal,
  action == Action::"delete",
  resource is Document
) when {
  principal.department == "security" &&
  resource.classification == "confidential"
};

forbid(
  principal,
  action == Action::"delete",
  resource is Document
) when {
  resource.classification == "confidential" &&
  principal.department != "security"
};
