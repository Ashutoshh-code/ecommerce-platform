package com.example.ecommerceplatform.CartService.Service.Impl;

import com.example.ecommerceplatform.CartService.Client.MerchantServiceClient;
import com.example.ecommerceplatform.CartService.Client.OrderServiceClient;
import com.example.ecommerceplatform.CartService.Client.ProductServiceClient;
import com.example.ecommerceplatform.CartService.Dto.AddCartItemRequestDto;
import com.example.ecommerceplatform.CartService.Dto.CartItemCountResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartItemResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CheckoutResponseDto;
import com.example.ecommerceplatform.CartService.Dto.PatchCartItemQuantityRequestDto;
import com.example.ecommerceplatform.CartService.Dto.request.CreateOrderRequest;
import com.example.ecommerceplatform.CartService.Dto.request.OrderLineItemRequest;
import com.example.ecommerceplatform.CartService.Dto.response.MerchantItemResponse;
import com.example.ecommerceplatform.CartService.Dto.response.MerchantItemStatus;
import com.example.ecommerceplatform.CartService.Dto.response.OrderCreatedResponse;
import com.example.ecommerceplatform.CartService.Dto.response.ProductImageResponse;
import com.example.ecommerceplatform.CartService.Entity.CartItems;
import com.example.ecommerceplatform.CartService.Entity.CartStatus;
import com.example.ecommerceplatform.CartService.Entity.Carts;
import com.example.ecommerceplatform.CartService.Exception.CartItemNotFoundException;
import com.example.ecommerceplatform.CartService.Exception.CartNotFoundException;
import com.example.ecommerceplatform.CartService.Exception.DownstreamServiceUnavailableException;
import com.example.ecommerceplatform.CartService.Exception.EmptyCartException;
import com.example.ecommerceplatform.CartService.Exception.InsufficientStockException;
import com.example.ecommerceplatform.CartService.Exception.ProductUnavailableException;
import com.example.ecommerceplatform.CartService.Repository.CartItemRepository;
import com.example.ecommerceplatform.CartService.Repository.CartRepository;
import com.example.ecommerceplatform.CartService.Service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final MerchantServiceClient merchantServiceClient;
    private final ProductServiceClient productServiceClient;
    private final OrderServiceClient orderServiceClient;

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

        CartItems existingLine = cartItemRepository.findByCartCartIdAndProductIdAndVariantIdAndMerchantId(
                cart.getCartId(), request.getProductId(), request.getVariantId(), request.getMerchantId()
        ).orElse(null);

        int alreadyInCart = existingLine == null ? 0 : existingLine.getQuantity();
        int requestedTotalQuantity = alreadyInCart + request.getQuantity();

        MerchantItemResponse merchantItem = merchantServiceClient.getItemDetails(
                request.getMerchantId(), request.getProductId(), request.getVariantId());
        requireSellable(merchantItem, requestedTotalQuantity);

        if (existingLine != null) {
            existingLine.setQuantity(requestedTotalQuantity);
            existingLine.setUnitPrice(merchantItem.getPrice());
            cartItemRepository.save(existingLine);
        } else {
            CartItems newLine = CartItems.builder()
                    .cart(cart)
                    .productId(request.getProductId())
                    .variantId(request.getVariantId())
                    .merchantId(request.getMerchantId())
                    .quantity(request.getQuantity())
                    .unitPrice(merchantItem.getPrice())
                    .build();
            cartItemRepository.save(newLine);
        }

        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        return toCartResponse(cart, items);
    }

    @Override
    @Transactional
    public CartResponseDto patchItemQuantity(UUID userId, UUID cartItemId, PatchCartItemQuantityRequestDto request) {
        Carts cart = requireActiveCart(userId);

        CartItems item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));
        if (!item.getCart().getCartId().equals(cart.getCartId())) {
            throw new CartItemNotFoundException(cartItemId);
        }

        int delta = request.getDelta();
        int newQuantity = item.getQuantity() + delta;

        if (newQuantity <= 0) {
            cartItemRepository.delete(item);
        } else if (delta > 0) {
            MerchantItemResponse merchantItem = merchantServiceClient.getItemDetails(
                    item.getMerchantId(), item.getProductId(), item.getVariantId());
            requireSellable(merchantItem, newQuantity);

            item.setQuantity(newQuantity);
            item.setUnitPrice(merchantItem.getPrice());
            cartItemRepository.save(item);
        } else {
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
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

    @Override
    @Transactional
    public CheckoutResponseDto checkout(UUID userId) {
        Carts cart = requireActiveCart(userId);

        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        if (items.isEmpty()) {
            throw new EmptyCartException(userId);
        }

        List<OrderLineItemRequest> orderLines = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (CartItems item : items) {
            MerchantItemResponse merchantItem = merchantServiceClient.getItemDetails(
                    item.getMerchantId(), item.getProductId(), item.getVariantId());
            requireSellable(merchantItem, item.getQuantity());

            // Re-priced at checkout time in case the merchant changed price since it was added to the cart.
            item.setUnitPrice(merchantItem.getPrice());
            cartItemRepository.save(item);

            BigDecimal lineTotal = merchantItem.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            totalPrice = totalPrice.add(lineTotal);

            orderLines.add(OrderLineItemRequest.builder()
                    .productId(item.getProductId())
                    .variantId(item.getVariantId())
                    .merchantId(item.getMerchantId())
                    .quantity(item.getQuantity())
                    .unitPrice(merchantItem.getPrice())
                    .lineTotal(lineTotal)
                    .build());
        }

        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .cartId(cart.getCartId())
                .userId(userId)
                .items(orderLines)
                .totalPrice(totalPrice)
                .build();

        OrderCreatedResponse orderResponse = orderServiceClient.createOrder(orderRequest);

        cart.setStatus(CartStatus.CHECKED_OUT);
        cartRepository.save(cart);

        return CheckoutResponseDto.builder()
                .orderId(orderResponse.getOrderId())
                .cartId(cart.getCartId())
                .userId(userId)
                .itemCount(items.size())
                .totalPrice(totalPrice)
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

    /**
     * DELISTED means the merchant pulled this exact listing (unrecoverable for this cart line);
     * OUT_OF_STOCK and an ACTIVE listing with too few units are both quantity problems the user
     * can retry with fewer units — kept as two distinct exceptions for that reason.
     */

    private void requireSellable(MerchantItemResponse merchantItem, int requestedQuantity) {
        if (merchantItem.getStatus() == MerchantItemStatus.DELISTED) {
            throw new ProductUnavailableException(merchantItem.getProductId());
        }
        int availableStock = merchantItem.getAvailableStock() == null ? 0 : merchantItem.getAvailableStock();
        if (merchantItem.getStatus() == MerchantItemStatus.OUT_OF_STOCK || availableStock < requestedQuantity) {
            throw new InsufficientStockException(merchantItem.getProductId(), requestedQuantity, availableStock);
        }
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
        boolean available = false;
        try {
            MerchantItemResponse merchantItem = merchantServiceClient.getItemDetails(
                    item.getMerchantId(), item.getProductId(), item.getVariantId());
            productName = merchantItem.getProductName();
            available = merchantItem.getStatus() == MerchantItemStatus.ACTIVE;
        } catch (DownstreamServiceUnavailableException ignored) {
            // Merchant Service is down/unreachable: show the cart with stored data, degrade display fields only.
        }

        String productImage = null;
        try {
            ProductImageResponse image = productServiceClient.getProductImage(item.getVariantId());
            productImage = image.getImageUrl();
        } catch (DownstreamServiceUnavailableException ignored) {
            // Product Service is down/unreachable: leave the image blank, degrade display only.
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
