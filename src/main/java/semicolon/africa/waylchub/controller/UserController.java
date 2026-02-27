package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    // Fetch the logged-in user's profile
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthenticated request");
        }
        return ResponseEntity.ok(userService.getCurrentUser(userDetails.getUserId()));
    }

    // Update the user's address
    @PutMapping("/me/address")
    public ResponseEntity<UserResponse> updateMyAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateUserAddressRequest request) {

        if (userDetails == null) {
            throw new RuntimeException("Unauthenticated request");
        }
        return ResponseEntity.ok(userService.updateUserAddress(userDetails.getUserId(), request));
    }
}