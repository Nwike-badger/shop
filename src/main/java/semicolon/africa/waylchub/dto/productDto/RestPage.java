package semicolon.africa.waylchub.dto.productDto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

/**
 * Serializable wrapper for PageImpl that survives a Redis round-trip.
 *
 * Spring Data's PageImpl has no @JsonCreator constructor, so Jackson cannot
 * reconstruct it from a Redis JSON blob. This class adds the correct
 * @JsonCreator and inherits all Page behaviour from PageImpl.
 * The return type of filterProducts() stays Page<T> — callers see no change.
 */
@JsonIgnoreProperties(value = "pageable", ignoreUnknown = true)
public class RestPage<T> extends PageImpl<T> {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RestPage(
            @JsonProperty("content")       List<T> content,
            @JsonProperty("number")        int number,
            @JsonProperty("size")          int size,
            @JsonProperty("totalElements") long totalElements) {
        super(content, PageRequest.of(number, Math.max(size, 1)), totalElements);
    }

    /** Wraps any Page for caching — call this inside filterProducts(). */
    public RestPage(Page<T> page) {
        super(page.getContent(), page.getPageable(), page.getTotalElements());
    }
}