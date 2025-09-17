package io.gravitee.am.model.safe;

import lombok.Data;

import java.util.List;

@Data
public class McpProperties {
    private List<String> tools;
    private List<String> scopes;
}
