package io.gravitee.am.management.handlers.management.api.bulk;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class BulkResponseTest {

    @Test
    void atLeastOneResultIsRequired() {
        List<BulkOperationResult<Void>> noResults = List.of();
        assertThatThrownBy(() -> new BulkResponse<>(noResults))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BulkResponse must contain at least 1 result");
    }

    @Test
    void resultsGetSortedByIndex() {
        var results = Stream.of(
                BulkOperationResult.success(Response.Status.OK,"a"),
                BulkOperationResult.success(Response.Status.OK,"b"),
                BulkOperationResult.success(Response.Status.OK,"c"),
                BulkOperationResult.success(Response.Status.OK,"d"),
                BulkOperationResult.success(Response.Status.OK,"e")
        ).map(x->x.withIndex((int)Math.floor(Math.random()*100)))
                .toList();
        
        var response = new BulkResponse<>(results);
        assertThat(response.getResults()).isSortedAccordingTo(Comparator.comparing(BulkOperationResult::index));
    }

}
