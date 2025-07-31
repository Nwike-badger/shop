package semicolon.africa.waylchub.dto.userDTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import semicolon.africa.waylchub.model.product.Address;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateUserAddressRequest {

    @NotNull(message = "Address details cannot be null")
    @Valid
    private Address address;
}
