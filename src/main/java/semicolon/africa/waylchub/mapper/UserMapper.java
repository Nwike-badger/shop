package semicolon.africa.waylchub.mapper;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.model.user.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UserMapper {

    // Used at login by CustomUserDetailsService — reads from the DB user.
    public static CustomUserDetails toCustomUserDetails(User user) {
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());

        return CustomUserDetails.builder()
                .userId(user.getId())
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

    // Used on every authenticated request by JwtAuthenticationFilter — rebuilds
    // the principal straight from the token's claims. No database hit.
    public static CustomUserDetails fromAccessTokenClaims(Claims claims) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        Object raw = claims.get("authorities");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {          // defensive: {"authority":"ROLE_X"}
                    Object a = map.get("authority");
                    if (a != null) authorities.add(new SimpleGrantedAuthority(a.toString()));
                } else if (item != null) {                     // normal: "ROLE_X"
                    authorities.add(new SimpleGrantedAuthority(item.toString()));
                }
            }
        }

        return CustomUserDetails.builder()
                .userId(claims.get("id", String.class))
                .username(claims.getSubject())
                .password(null)                 // not needed once already authenticated
                .firstName(claims.get("firstName", String.class))
                .lastName(claims.get("lastName", String.class))
                .phoneNumber(claims.get("phoneNumber", String.class))
                .verified(Boolean.TRUE.equals(claims.get("verified")))
                .accountNonExpired(true)        // validated at login; token is short-lived
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .enabled(true)
                .authorities(authorities)
                .build();
    }
}