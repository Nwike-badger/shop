package semicolon.africa.waylchub.mapper;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.dto.productDto.ProductResponseDto;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.user.User;

import java.util.List;
import java.util.stream.Collectors;

public class UserMapper {

    public static CustomUserDetails toCustomUserDetails(User user) {
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());

        return CustomUserDetails.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .verified(user.isVerified())
                .accountNonExpired(user.isAccountNonExpired())
                .accountNonLocked(user.isAccountNonLocked())
                .credentialsNonExpired(user.isCredentialsNonExpired())
                .enabled(user.isEnabled())
                .authorities(authorities)
                .build();
    }

    private ProductResponseDto toResponseDTO(Product product) {
        return ProductResponseDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .imageUrls(product.getImageUrls())
                .category(product.getCategory())
                .subCategory(product.getSubCategory())
                .quantityAvailable(product.getQuantityAvailable())
                .quantitySold(product.getQuantitySold())
                .isBestSeller(product.isBestSeller())
                .isNewArrival(product.isNewArrival())
                .createdAt(product.getCreatedAt())
                .brand(product.getBrand())
                .rating(product.getRating())
                .build();
    }
}
