package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.dto.userDTO.UpdateUserAddressRequest;
import semicolon.africa.waylchub.dto.userDTO.UserResponse;
import semicolon.africa.waylchub.service.userService.UserService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) throw new RuntimeException("Unauthenticated request");
        return ResponseEntity.ok(userService.getCurrentUser(userDetails.getUserId()));
    }

    @PutMapping("/me/address")
    public ResponseEntity<UserResponse> updateMyAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateUserAddressRequest request) {
        if (userDetails == null) throw new RuntimeException("Unauthenticated request");
        return ResponseEntity.ok(userService.updateUserAddress(userDetails.getUserId(), request));
    }

    /**
     * Promotes any registered user to ADMIN role.
     * Only callable by an existing ADMIN — secured at the method level.
     *
     * For your FIRST ever admin: use AdminDataInitializer (see that file).
     * After that, use this endpoint from Postman with your admin JWT.
     */
    @PutMapping("/promote/{email}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<UserResponse> promoteUserToAdmin(@PathVariable String email) {
        return ResponseEntity.ok(userService.promoteToAdmin(email));
    }
}