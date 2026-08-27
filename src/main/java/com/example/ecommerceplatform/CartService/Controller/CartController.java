package com.example.ecommerceplatform.CartService.Controller;

import com.example.ecommerceplatform.CartService.Dto.AddCartItemRequestDto;
import com.example.ecommerceplatform.CartService.Dto.BuyNowResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartItemCountResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartValidationResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CheckoutAddressRequestDto;
import com.example.ecommerceplatform.CartService.Dto.CheckoutResponseDto;
import com.example.ecommerceplatform.CartService.Dto.PatchCartItemQuantityRequestDto;
import com.example.ecommerceplatform.CartService.Dto.PatchCartItemQuantityResponseDto;
import com.example.ecommerceplatform.CartService.Service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * userId comes from the X-User-Id header on every endpoint here - it's set
 * by the API Gateway (JwtAuthenticationFilter/UserContextRequestWrapper)
 * after verifying the caller's accessToken cookie, never from the URL or
 * request body. The frontend never needs to know or send its own user id.
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    //Working
    @GetMapping
    public ResponseEntity<CartResponseDto> getCart(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    //Working
    @PostMapping("/items")
    public ResponseEntity<CartResponseDto> addItem(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AddCartItemRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.addItemToCart(userId, request));
    }

    //Working
    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<PatchCartItemQuantityResponseDto> patchItemQuantity(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID cartItemId,
            @Valid @RequestBody PatchCartItemQuantityRequestDto request
    ) {
        return ResponseEntity.ok(cartService.patchItemQuantity(userId, cartItemId, request));
    }

    //Working
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponseDto> removeItem(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID cartItemId
    ) {
        return ResponseEntity.ok(cartService.removeItem(userId, cartItemId));
    }

    //Working
    @DeleteMapping("/items")
    public ResponseEntity<Void> clearCart(@RequestHeader("X-User-Id") UUID userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    //Working
    @GetMapping("/count")
    public ResponseEntity<CartItemCountResponseDto> getItemCount(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(cartService.getItemCount(userId));
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponseDto> checkout(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody(required = false) CheckoutAddressRequestDto customAddress
    ) {
        return ResponseEntity.ok(cartService.checkout(userId, customAddress));
    }

    @PostMapping("/validate")
    public ResponseEntity<CartValidationResponseDto> validateCart(@RequestHeader("X-User-Id") UUID userId) {
        CartValidationResponseDto result = cartService.validateCart(userId);
        HttpStatus status = result.isValid() ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(result);
    }

    @PostMapping("/buy-now")
    public ResponseEntity<BuyNowResponseDto> buyNow(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AddCartItemRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.buyNow(userId, request));
    }

    @PostMapping("/checkout/{cartItemId}")
    public ResponseEntity<CheckoutResponseDto> checkoutItem(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID cartItemId,
            @Valid @RequestBody(required = false) CheckoutAddressRequestDto customAddress
    ) {
        return ResponseEntity.ok(cartService.checkoutItem(userId, cartItemId, customAddress));
    }
}