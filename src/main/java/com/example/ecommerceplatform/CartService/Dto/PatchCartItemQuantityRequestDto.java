package com.example.ecommerceplatform.CartService.Dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatchCartItemQuantityRequestDto {

    @NotNull(message = "delta is required")
    private Integer delta;
}
