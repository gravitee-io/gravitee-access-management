package io.gravitee.am.common.event;

public enum ProtectedResourceEvent {
    DEPLOY,
    UPDATE,
    UNDEPLOY;

    public static ProtectedResourceEvent actionOf(Action action) {
        return switch (action) {
            case CREATE -> ProtectedResourceEvent.DEPLOY;
            case UPDATE -> ProtectedResourceEvent.UPDATE;
            case DELETE -> ProtectedResourceEvent.UNDEPLOY;
            default -> null;
        };
    }
}
