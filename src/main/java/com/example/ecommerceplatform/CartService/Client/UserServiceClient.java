package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.request.AddressRequestDto;
import com.example.ecommerceplatform.CartService.Dto.response.AddressResponseDto;
import com.example.ecommerceplatform.CartService.Dto.response.UserDetailsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * Cart Service's contract for User Service. Used at checkout to both confirm a userId
 * is real and snapshot identity/address details onto the order, now that Order Service
 * no longer calls User Service itself (UserController#getUser — GET /api/users/{userId};
 * AddressController#getDefaultAddress — GET /api/users/{userId}/addresses/default;
 * AddressController#addAddress — POST /api/users/{userId}/addresses, used when the
 * checkout caller supplies a one-off address instead of using their default).
 * Falls back via {@link UserServiceClientFallbackFactory}, which distinguishes a real
 * "not found" (404) from User Service actually being unreachable.
 */
@FeignClient(
        name = "user-service",
        url = "${clients.user-service.url}",
        fallbackFactory = UserServiceClientFallbackFactory.class
)
public interface UserServiceClient {

    @GetMapping("/api/users/{userId}")
    UserDetailsResponse getUser(@PathVariable("userId") UUID userId);

    @GetMapping("/api/users/{userId}/addresses/default")
    AddressResponseDto getDefaultAddress(@PathVariable("userId") UUID userId);

    @PostMapping("/api/users/{userId}/addresses")
    AddressResponseDto createAddress(@PathVariable("userId") UUID userId, @RequestBody AddressRequestDto request);
}
