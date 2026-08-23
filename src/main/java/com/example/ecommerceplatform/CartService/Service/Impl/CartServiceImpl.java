package com.example.ecommerceplatform.CartService.Service.Impl;

import com.example.ecommerceplatform.CartService.Client.MerchantServiceClient;
import com.example.ecommerceplatform.CartService.Client.ProductServiceClient;
import com.example.ecommerceplatform.CartService.Dto.AddCartItemRequestDto;
import com.example.ecommerceplatform.CartService.Dto.CartItemCountResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartItemResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartResponseDto;
import com.example.ecommerceplatform.CartService.Dto.response.ProductDetailsResponse;
import com.example.ecommerceplatform.CartService.Dto.response.StockPriceResponse;
import com.example.ecommerceplatform.CartService.Entity.CartItems;
import com.example.ecommerceplatform.CartService.Entity.CartStatus;
import com.example.ecommerceplatform.CartService.Entity.Carts;
import com.example.ecommerceplatform.CartService.Exception.CartItemNotFoundException;
import com.example.ecommerceplatform.CartService.Exception.CartNotFoundException;
import com.example.ecommerceplatform.CartService.Exception.DownstreamServiceUnavailableException;
import com.example.ecommerceplatform.CartService.Exception.InsufficientStockException;
import com.example.ecommerceplatform.CartService.Exception.ProductUnavailableException;
import com.example.ecommerceplatform.CartService.Repository.CartItemRepository;
import com.example.ecommerceplatform.CartService.Repository.CartRepository;
import com.example.ecommerceplatform.CartService.Service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductServiceClient productServiceClient;
    private final MerchantServiceClient merchantServiceClient;

    @Override
    @Transactional(readOnly = true)
    public CartResponseDto getCart(UUID userId) {
        Carts cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
        if (cart == null) {
            return emptyCartResponse(userId);
        }
        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        return toCartResponse(cart, items);
    }

    @Override
    @Transactional
    public CartResponseDto addItemToCart(UUID userId, AddCartItemRequestDto request) {
        Carts cart = getOrCreateActiveCart(userId);

        ProductDetailsResponse product = productServiceClient.getProductDetails(
                request.getProductId(), request.getVariantId());
        if (!product.isAvailable()) {
            throw new ProductUnavailableException(request.getProductId());
        }

        CartItems existingLine = cartItemRepository.findMatchingLine(
                cart.getCartId(), request.getProductId(), request.getVariantId(), request.getMerchantId()
        ).orElse(null);

        int alreadyInCart = existingLine == null ? 0 : existingLine.getQuantity();
        int requestedTotalQuantity = alreadyInCart + request.getQuantity();

        StockPriceResponse stock = merchantServiceClient.getStockAndPrice(
                request.getMerchantId(), request.getProductId(), request.getVariantId());
        if (!stock.isInStock() || stock.getAvailableStock() < requestedTotalQuantity) {
            int available = stock.getAvailableStock() == null ? 0 : stock.getAvailableStock();
            throw new InsufficientStockException(request.getProductId(), requestedTotalQuantity, available);
        }

        if (existingLine != null) {
            existingLine.setQuantity(requestedTotalQuantity);
            existingLine.setUnitPrice(stock.getPrice());
            cartItemRepository.save(existingLine);
        } else {
            CartItems newLine = CartItems.builder()
                    .cart(cart)
                    .productId(request.getProductId())
                    .variantId(request.getVariantId())
                    .merchantId(request.getMerchantId())
                    .quantity(request.getQuantity())
                    .unitPrice(stock.getPrice())
                    .build();
            cartItemRepository.save(newLine);
        }

        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        return toCartResponse(cart, items);
    }

    @Override
    @Transactional
    public CartResponseDto removeItem(UUID userId, UUID cartItemId) {
        Carts cart = requireActiveCart(userId);

        CartItems item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));
        if (!item.getCart().getCartId().equals(cart.getCartId())) {
            throw new CartItemNotFoundException(cartItemId);
        }

        cartItemRepository.delete(item);

        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        return toCartResponse(cart, items);
    }

    @Override
    @Transactional
    public void clearCart(UUID userId) {
        Carts cart = requireActiveCart(userId);
        cartItemRepository.deleteByCartCartId(cart.getCartId());
    }

    @Override
    @Transactional(readOnly = true)
    public CartItemCountResponseDto getItemCount(UUID userId) {
        Carts cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
        if (cart == null) {
            return CartItemCountResponseDto.builder()
                    .userId(userId)
                    .distinctItemCount(0)
                    .totalQuantity(0)
                    .build();
        }
        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        long totalQuantity = items.stream().mapToLong(CartItems::getQuantity).sum();

        return CartItemCountResponseDto.builder()
                .cartId(cart.getCartId())
                .userId(userId)
                .distinctItemCount(items.size())
                .totalQuantity(totalQuantity)
                .build();
    }

    private Carts getOrCreateActiveCart(UUID userId) {
        Carts cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
        if (cart != null) {
            return cart;
        }
        Carts newCart = Carts.builder()
                .userId(userId)
                .status(CartStatus.ACTIVE)
                .build();
        return cartRepository.save(newCart);
    }

    private Carts requireActiveCart(UUID userId) {
        Carts cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
        if (cart == null) {
            throw new CartNotFoundException(userId);
        }
        return cart;
    }

    private CartResponseDto emptyCartResponse(UUID userId) {
        return CartResponseDto.builder()
                .userId(userId)
                .status(CartStatus.ACTIVE)
                .items(List.of())
                .totalItemCount(0)
                .totalPrice(BigDecimal.ZERO)
                .build();
    }

    private CartResponseDto toCartResponse(Carts cart, List<CartItems> items) {
        List<CartItemResponseDto> itemDtos = items.stream()
                .map(this::toCartItemResponse)
                .toList();

        BigDecimal totalPrice = itemDtos.stream()
                .map(CartItemResponseDto::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponseDto.builder()
                .cartId(cart.getCartId())
                .userId(cart.getUserId())
                .status(cart.getStatus())
                .items(itemDtos)
                .totalItemCount(itemDtos.size())
                .totalPrice(totalPrice)
                .build();
    }

    private CartItemResponseDto toCartItemResponse(CartItems item) {
        BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

        String productName = "Unavailable";
        String productImage = null;
        boolean available = false;
        try {
            ProductDetailsResponse product = productServiceClient.getProductDetails(
                    item.getProductId(), item.getVariantId());
            productName = product.getName();
            productImage = product.getImageUrl();
            available = product.isAvailable();
        } catch (DownstreamServiceUnavailableException ignored) {
            // Product Service is down/unreachable: show the cart with stored data, degrade display fields only.
        }

        return CartItemResponseDto.builder()
                .cartItemId(item.getId())
                .productId(item.getProductId())
                .variantId(item.getVariantId())
                .merchantId(item.getMerchantId())
                .productName(productName)
                .productImage(productImage)
                .available(available)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .lineTotal(lineTotal)
                .build();
    }
}
