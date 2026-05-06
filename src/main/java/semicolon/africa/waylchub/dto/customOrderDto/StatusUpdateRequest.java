package semicolon.africa.waylchub.dto.customOrderDto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import semicolon.africa.waylchub.model.customOrder.CustomOrderStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusUpdateRequest {

    @NotNull
    private CustomOrderStatus newStatus;

    @Size(max = 500)
    private String note;
}