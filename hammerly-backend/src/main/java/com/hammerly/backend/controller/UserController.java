package com.hammerly.backend.controller;

import com.hammerly.backend.dto.UserDtos.AddPaymentMethodRequest;
import com.hammerly.backend.dto.UserDtos.ChangePasswordRequest;
import com.hammerly.backend.dto.UserDtos.UpdateAvatarRequest;
import com.hammerly.backend.dto.UserDtos.UpdateProfileRequest;
import com.hammerly.backend.security.AuthenticatedUser;
import com.hammerly.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    @Operation(summary = "Get current user profile", tags = "Users", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> profile(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.profile(user.userId());
    }

    @PutMapping("/profile")
    @Operation(summary = "Update current user profile", tags = "Users", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                      @RequestBody UpdateProfileRequest request) {
        return service.updateProfile(user.userId(), request);
    }

    @PutMapping("/profile/password")
    @Operation(summary = "Change current user password", tags = "Users", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                       @RequestBody ChangePasswordRequest request) {
        return service.changePassword(user.userId(), request);
    }

    @PutMapping("/profile/avatar")
    @Operation(summary = "Update current user avatar", tags = "Users", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> updateAvatar(@AuthenticationPrincipal AuthenticatedUser user,
                                     @RequestBody UpdateAvatarRequest request) {
        return service.updateAvatar(user.userId(), request == null ? null : request.avatarImage());
    }

    @DeleteMapping("/profile/avatar")
    @Operation(summary = "Remove current user avatar", tags = "Users", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> removeAvatar(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.removeAvatar(user.userId());
    }

    @GetMapping("/profile/payment-methods")
    @Operation(summary = "Get payment methods", tags = "Users", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> paymentMethods(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.paymentMethods(user.userId());
    }

    @PostMapping("/profile/payment-methods")
    @Operation(summary = "Add a payment method", tags = "Users", security = @SecurityRequirement(name = "BearerAuth"))
    ResponseEntity<Map<String, Object>> addPaymentMethod(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @RequestBody AddPaymentMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addPaymentMethod(user.userId(), request));
    }

    @DeleteMapping("/profile/payment-methods/{id}")
    @Operation(summary = "Delete a payment method", tags = "Users", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> deletePaymentMethod(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable String id) {
        return service.deletePaymentMethod(user.userId(), id);
    }

    @PutMapping("/profile/payment-methods/{id}/default")
    @Operation(summary = "Set default payment method", tags = "Users", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> setDefaultPaymentMethod(@AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable String id) {
        return service.setDefaultPaymentMethod(user.userId(), id);
    }

    @GetMapping("/my-bids")
    @Operation(summary = "Get current user's bids", tags = "Users", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> myBids(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.myBids(user.userId());
    }

    @GetMapping("/my-auctions")
    @Operation(summary = "Get current user's auctions", tags = "Users", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> myAuctions(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.myAuctions(user.userId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get public user information", tags = "Users")
    Map<String, Object> publicUser(@PathVariable String id) {
        return service.publicUser(id);
    }
}
