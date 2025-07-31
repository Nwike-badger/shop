package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.userDTO.CustomUserDetails;
import semicolon.africa.waylchub.dto.userDTO.UpdateUserAddressRequest;
import semicolon.africa.waylchub.dto.userDTO.UserRegistrationRequest;
import semicolon.africa.waylchub.dto.userDTO.UserResponse;
import semicolon.africa.waylchub.service.userService.UserService;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody UserRegistrationRequest request) {
        UserResponse response = userService.register(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/address")
    public ResponseEntity<UserResponse> updateUserAddress(@Valid @RequestBody UpdateUserAddressRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String userId = userDetails.getUserId();

        UserResponse response = userService.updateUserAddress(userId, request);
        return ResponseEntity.ok(response);
    }
}
