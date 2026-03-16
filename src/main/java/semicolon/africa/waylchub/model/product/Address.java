package semicolon.africa.waylchub.model.product;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class Address {
    private String streetAddress;
    private String apartmentNo;
    private String city;
    private String state;
    private String zipCode; // Or postalCode
    private String phoneNumber;
    private String emailAddress; // Useful for shipping confirmation
    private String companyName; // Optional
}
