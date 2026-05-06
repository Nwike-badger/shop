package semicolon.africa.waylchub.dto.customOrderDto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Admin payload to apply a quote to a SUBMITTED order. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteRequest {

    @NotNull
    @DecimalMin(value = "0.01", message = "Quoted amount must be greater than zero")
    private BigDecimal quotedAmount;

    @Size(max = 1000)
    private String quoteNotes;
}