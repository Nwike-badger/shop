package semicolon.africa.waylchub.model.product;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document; // Not a document itself, but a component

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
// This is likely an embedded document or a simple class
// If it's a standalone Document, remove @Document from Order.java for shippingAddress
// and use DBRef or String ID
public class Address {
    private String streetAddress;
    private String apartmentNo;
    private String townCity;
    private String state;
    private String zipCode; // Or postalCode
    private String phoneNumber;
    private String emailAddress; // Useful for shipping confirmation
    private String companyName; // Optional
}
