package com.example.ecommerceplatform.CartService.Controller;

import com.example.ecommerceplatform.CartService.Dto.AddCartItemRequestDto;
import com.example.ecommerceplatform.CartService.Dto.CartItemCountResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartResponseDto;
import com.example.ecommerceplatform.CartService.Service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/{userId}")
    public ResponseEntity<CartResponseDto> getCart(@PathVariable UUID userId) {
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PostMapping("/{userId}/items")
    public ResponseEntity<CartResponseDto> addItem(
            @PathVariable UUID userId,
            @Valid @RequestBody AddCartItemRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.addItemToCart(userId, request));
    }

    @DeleteMapping("/{userId}/items/{cartItemId}")
    public ResponseEntity<CartResponseDto> removeItem(
            @PathVariable UUID userId,
            @PathVariable UUID cartItemId
    ) {
        return ResponseEntity.ok(cartService.removeItem(userId, cartItemId));
    }

    @DeleteMapping("/{userId}/items")
    public ResponseEntity<Void> clearCart(@PathVariable UUID userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/count")
    public ResponseEntity<CartItemCountResponseDto> getItemCount(@PathVariable UUID userId) {
        return ResponseEntity.ok(cartService.getItemCount(userId));
    }
}
