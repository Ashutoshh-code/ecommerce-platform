package com.example.ecommerceplatform.CartService.Dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Contract expected back from User Service (GET /api/users/{userId}). Only the fields
 * Cart Service needs to snapshot onto the order — extra fields on the real UserResponse
 * (phone, role, etc.) are simply ignored.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDetailsResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
}
