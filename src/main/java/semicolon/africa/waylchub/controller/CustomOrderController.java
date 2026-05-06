package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.customOrderDto.*;
import semicolon.africa.waylchub.exception.UserNotFoundException;
import semicolon.africa.waylchub.model.customOrder.CustomOrder;
import semicolon.africa.waylchub.model.customOrder.CustomOrderStatus;
import semicolon.africa.waylchub.model.user.User;
import semicolon.africa.waylchub.repository.userRepository.UserRepository;
import semicolon.africa.waylchub.service.customOrderService.CustomOrderService;

/**
 * REST endpoints for custom (made-to-measure) orders.
 *
 * URL prefix is /api/v1/ to match the rest of the codebase (auth, cart, orders).
 * The frontend axios baseURL ends in /api so it calls /v1/custom-orders relative.
 *
 * Endpoints:
 *   POST /api/v1/custom-orders                — submit (client, guest-ok)
 *   GET  /api/v1/custom-orders/me             — my orders (authenticated client)
 *   GET  /api/v1/custom-orders/{ref}          — lookup by reference (client, guest-ok)
 *
 *   GET   /api/v1/custom-orders               — list all with optional status filter (admin)
 *   POST  /api/v1/custom-orders/{ref}/quote   — apply quote (admin)
 *   POST  /api/v1/custom-orders/{ref}/status  — update status (admin)
 *
 * Why UserRepository is injected here: Spring's UserDetails interface only
 * exposes getUsername() (which in this codebase returns the email). To store
 * the real User._id as the order's customerId — matching the existing Order
 * entity's pattern — we resolve username -> User on each authenticated request.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/custom-orders")
@RequiredArgsConstructor
public class CustomOrderController {

    private final CustomOrderService service;
    private final UserRepository userRepository;

    // ─── Client endpoints ────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<CustomOrderResponse> submit(
            @Valid @RequestBody CustomOrderRequest request,
            @AuthenticationPrincipal UserDetails user) {

        // null user = guest submission; authenticated user → resolve real ID
        String customerId = resolveUserId(user);
        CustomOrder order = service.submit(request, customerId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(CustomOrderResponse.from(order));
    }

    @GetMapping("/{ref}")
    public ResponseEntity<CustomOrderResponse> getByReference(@PathVariable String ref) {
        CustomOrder order = service.getByReference(ref);
        return ResponseEntity.ok(CustomOrderResponse.from(order));
    }

    @GetMapping("/me")
    public ResponseEntity<Page<CustomOrderSummary>> myOrders(
            @AuthenticationPrincipal UserDetails user,
            @PageableDefault(size = 20) Pageable pageable) {

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String customerId = resolveUserId(user);
        Page<CustomOrder> page = service.listForCustomer(customerId, pageable);
        return ResponseEntity.ok(page.map(CustomOrderSummary::from));
    }

    // ─── Admin endpoints ─────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<CustomOrderSummary>> listAll(
            @RequestParam(required = false) CustomOrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<CustomOrder> page = (status != null)
                ? service.listByStatus(status, pageable)
                : service.listAll(pageable);

        return ResponseEntity.ok(page.map(CustomOrderSummary::from));
    }

    @PostMapping("/{ref}/quote")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustomOrderResponse> applyQuote(
            @PathVariable String ref,
            @Valid @RequestBody QuoteRequest request,
            @AuthenticationPrincipal UserDetails admin) {

        String adminId = resolveUserId(admin);
        CustomOrder order = service.applyQuote(ref, request, adminId);
        return ResponseEntity.ok(CustomOrderResponse.from(order));
    }

    @PostMapping("/{ref}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustomOrderResponse> updateStatus(
            @PathVariable String ref,
            @Valid @RequestBody StatusUpdateRequest request,
            @AuthenticationPrincipal UserDetails admin) {

        String adminId = resolveUserId(admin);
        CustomOrder order = service.updateStatus(ref, request.getNewStatus(), request.getNote(), adminId);
        return ResponseEntity.ok(CustomOrderResponse.from(order));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    /**
     * Resolves a UserDetails (which exposes only username == email in this codebase)
     * to the actual User entity's MongoDB id, matching the convention used by
     * the existing Order entity. Returns null for unauthenticated callers.
     */
    private String resolveUserId(UserDetails userDetails) {
        if (userDetails == null) return null;
        return userRepository.findByUsername(userDetails.getUsername())
                .map(User::getId)
                .orElseThrow(() -> new UserNotFoundException(
                        "Authenticated user not found in database: " + userDetails.getUsername()));
    }
}