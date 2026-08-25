package com.example.ecommerceplatform.CartService.Dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors Merchant Service's own listing status enum (its "status" field on the
 * stock/price response). The @JsonProperty values must match Merchant Service's
 * lowercase wire format exactly, since Cart Service doesn't own this contract.
 */
public enum MerchantItemStatus {

    @JsonProperty("active")
    ACTIVE,

    @JsonProperty("out_of_stock")
    OUT_OF_STOCK,

    @JsonProperty("delisted")
    DELISTED
}
