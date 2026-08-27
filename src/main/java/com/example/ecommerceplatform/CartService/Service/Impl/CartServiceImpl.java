package com.example.ecommerceplatform.CartService.Service.Impl;

import com.example.ecommerceplatform.CartService.Client.MerchantServiceClient;
import com.example.ecommerceplatform.CartService.Client.OrderServiceClient;
import com.example.ecommerceplatform.CartService.Client.ProductServiceClient;
import com.example.ecommerceplatform.CartService.Client.UserServiceClient;
import com.example.ecommerceplatform.CartService.Dto.AddCartItemRequestDto;
import com.example.ecommerceplatform.CartService.Dto.BuyNowResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartItemCountResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartItemResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartValidationResponseDto;
import com.example.ecommerceplatform.CartService.Dto.ChangedPriceItemDto;
import com.example.ecommerceplatform.CartService.Dto.CheckoutAddressRequestDto;
import com.example.ecommerceplatform.CartService.Dto.CheckoutResponseDto;
import com.example.ecommerceplatform.CartService.Dto.PatchCartItemQuantityRequestDto;
import com.example.ecommerceplatform.CartService.Dto.PatchCartItemQuantityResponseDto;
import com.example.ecommerceplatform.CartService.Dto.UnavailableCartItemDto;
import com.example.ecommerceplatform.CartService.Dto.ValidatedCartItemDto;
import com.example.ecommerceplatform.CartService.Dto.request.AddressRequestDto;
import com.example.ecommerceplatform.CartService.Dto.request.CreateOrderRequest;
import com.example.ecommerceplatform.CartService.Dto.request.OrderLineItemRequest;
import com.example.ecommerceplatform.CartService.Dto.request.ReserveStockRequestDto;
import com.example.ecommerceplatform.CartService.Dto.request.ShippingAddressRequest;
import com.example.ecommerceplatform.CartService.Dto.request.StockAvailabilityRequestDto;
import com.example.ecommerceplatform.CartService.Dto.response.AddressResponseDto;
import com.example.ecommerceplatform.CartService.Dto.response.OrderCreatedResponse;
import com.example.ecommerceplatform.CartService.Dto.response.ProductResponseDto;
import com.example.ecommerceplatform.CartService.Dto.response.StockAvailabilityResponseDto;
import com.example.ecommerceplatform.CartService.Dto.response.StockReservationResponseDto;
import com.example.ecommerceplatform.CartService.Dto.response.UserDetailsResponse;
import com.example.ecommerceplatform.CartService.Entity.CartItems;
import com.example.ecommerceplatform.CartService.Entity.CartStatus;
import com.example.ecommerceplatform.CartService.Entity.Carts;
import com.example.ecommerceplatform.CartService.Exception.CartItemNotFoundException;
import com.example.ecommerceplatform.CartService.Exception.CartNotFoundException;
import com.example.ecommerceplatform.CartService.Exception.DownstreamServiceUnavailableException;
import com.example.ecommerceplatform.CartService.Exception.DuplicateOrderException;
import com.example.ecommerceplatform.CartService.Exception.EmptyCartException;
import com.example.ecommerceplatform.CartService.Exception.InsufficientStockException;
import com.example.ecommerceplatform.CartService.Exception.OrderCreationFailedException;
import com.example.ecommerceplatform.CartService.Exception.ProductNotFoundException;
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
    private final UserServiceClient userServiceClient;

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

        StockAvailabilityResponseDto availability = merchantServiceClient.checkAvailability(
                StockAvailabilityRequestDto.builder()
                        .merchantId(request.getMerchantId())
                        .variantId(request.getVariantId())
                        .quantity(requestedTotalQuantity)
                        .build());
        requireSellable(availability, request.getProductId());

        if (existingLine != null) {
            existingLine.setQuantity(requestedTotalQuantity);
            existingLine.setUnitPrice(availability.getPrice());
            cartItemRepository.save(existingLine);
        } else {
            CartItems newLine = CartItems.builder()
                    .cart(cart)
                    .productId(request.getProductId())
                    .variantId(request.getVariantId())
                    .merchantId(request.getMerchantId())
                    .quantity(request.getQuantity())
                    .unitPrice(availability.getPrice())
                    .build();
            cartItemRepository.save(newLine);
        }

        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        return toCartResponse(cart, items);
    }

    @Override
    @Transactional
    public PatchCartItemQuantityResponseDto patchItemQuantity(UUID userId, UUID cartItemId, PatchCartItemQuantityRequestDto request) {
        Carts cart = requireActiveCart(userId);

        CartItems item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));
        if (!item.getCart().getCartId().equals(cart.getCartId())) {
            throw new CartItemNotFoundException(cartItemId);
        }

        int newQuantity = request.getQuantity();

        if (newQuantity > item.getQuantity()) {
            StockAvailabilityResponseDto availability = merchantServiceClient.checkAvailability(
                    StockAvailabilityRequestDto.builder()
                            .merchantId(item.getMerchantId())
                            .variantId(item.getVariantId())
                            .quantity(newQuantity)
                            .build());
            requireSellable(availability, item.getProductId());

            item.setQuantity(newQuantity);
            item.setUnitPrice(availability.getPrice());
            cartItemRepository.save(item);
        } else {
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        }

        BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        BigDecimal cartTotalPrice = items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PatchCartItemQuantityResponseDto.builder()
                .cartItemId(item.getId())
                .quantity(item.getQuantity())
                .lineTotal(lineTotal)
                .cartTotalPrice(cartTotalPrice)
                .build();
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
    public CheckoutResponseDto checkout(UUID userId, CheckoutAddressRequestDto customAddress) {
        // Order Service no longer calls User Service itself, so Cart Service fetches and
        // snapshots the customer's identity and address here instead.
        UserDetailsResponse user = userServiceClient.getUser(userId);
        AddressResponseDto address = resolveAddress(userId, customAddress);

        Carts cart = requireActiveCart(userId);

        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        if (items.isEmpty()) {
            throw new EmptyCartException(userId);
        }

        List<OrderLineItemRequest> orderLines = new ArrayList<>();
        List<ReserveStockRequestDto> reservedSoFar = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        try {
            for (CartItems item : items) {
                // Read-only pre-check: cheap fail-fast, and the only source of current price
                // (the atomic /reserve response below doesn't include price).
                StockAvailabilityResponseDto availability = merchantServiceClient.checkAvailability(
                        StockAvailabilityRequestDto.builder()
                                .merchantId(item.getMerchantId())
                                .variantId(item.getVariantId())
                                .quantity(item.getQuantity())
                                .build());
                requireSellable(availability, item.getProductId());

                // The actual concurrency-safe gate: Merchant Service atomically decrements
                // stock here, so whichever concurrent checkout's reserve call lands first at
                // its database wins; the other gets success=false, not a race.
                ReserveStockRequestDto reserveRequest = ReserveStockRequestDto.builder()
                        .merchantId(item.getMerchantId())
                        .variantId(item.getVariantId())
                        .quantity(item.getQuantity())
                        .build();
                StockReservationResponseDto reservation = merchantServiceClient.reserveStock(reserveRequest);
                requireReserved(reservation, item.getProductId());
                reservedSoFar.add(reserveRequest);

                // Order Service requires productName (non-blank) on every line item, so this is a
                // hard requirement here, unlike the best-effort/degrade-to-null lookup on GET cart.
                ProductResponseDto product = productServiceClient.getProduct(item.getProductId());

                // Re-priced at checkout time in case the merchant changed price since it was added to the cart.
                item.setUnitPrice(availability.getPrice());
                cartItemRepository.save(item);

                BigDecimal lineTotal = availability.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                totalPrice = totalPrice.add(lineTotal);

                orderLines.add(OrderLineItemRequest.builder()
                        .cartItemId(item.getId())
                        .productId(item.getProductId())
                        .variantId(item.getVariantId())
                        .merchantId(item.getMerchantId())
                        .productName(product.getName())
                        .imageUrl(product.getThumbnail())
                        .quantity(item.getQuantity())
                        .unitPrice(availability.getPrice())
                        .lineTotal(lineTotal)
                        .build());
            }
        } catch (InsufficientStockException | ProductUnavailableException | ProductNotFoundException
                 | DownstreamServiceUnavailableException ex) {
            // Same reasoning as below: only release for the specific, known failure modes of
            // these calls, not any RuntimeException, so a response-parsing bug after a real
            // success can't be mistaken for "the call failed."
            releaseAll(reservedSoFar);
            throw ex;
        }

        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .cartId(cart.getCartId())
                .userId(userId)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .shippingAddress(ShippingAddressRequest.builder()
                        .addressLine1(address.getAddressLine1())
                        .addressLine2(address.getAddressLine2())
                        .city(address.getCity())
                        .state(address.getState())
                        .country(address.getCountry())
                        .pincode(address.getPincode())
                        .build())
                .items(orderLines)
                .totalPrice(totalPrice)
                .build();

        OrderCreatedResponse orderResponse;
        try {
            orderResponse = orderServiceClient.createOrder(orderRequest);
        } catch (DownstreamServiceUnavailableException | OrderCreationFailedException | DuplicateOrderException ex) {
            // The reservations already succeeded at Merchant Service — if Order Service
            // genuinely rejected or couldn't be reached, that stock must be released or it's
            // lost inventory forever. Deliberately NOT catching RuntimeException broadly here:
            // if createOrder() actually succeeded but this app then failed to parse its
            // response, the order still exists at Order Service — releasing stock in that case
            // would create a phantom real order with no reserved stock behind it. Let that case
            // surface as a real error instead of silently "recovering" incorrectly.
            releaseAll(reservedSoFar);
            throw ex;
        }

        cart.setStatus(CartStatus.CHECKED_OUT);
        cartRepository.save(cart);

        int totalUnits = items.stream().mapToInt(CartItems::getQuantity).sum();

        return CheckoutResponseDto.builder()
                .orderId(orderResponse.getId())
                .cartId(cart.getCartId())
                .userId(userId)
                .itemCount(totalUnits)
                .totalPrice(totalPrice)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CartValidationResponseDto validateCart(UUID userId) {
        Carts cart = requireActiveCart(userId);

        List<CartItems> items = cartItemRepository.findByCartCartId(cart.getCartId());
        if (items.isEmpty()) {
            throw new EmptyCartException(userId);
        }

        List<UnavailableCartItemDto> delisted = new ArrayList<>();
        List<UnavailableCartItemDto> outOfStock = new ArrayList<>();
        List<ChangedPriceItemDto> priceChanged = new ArrayList<>();
        List<ValidatedCartItemDto> validItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItems item : items) {
            StockAvailabilityResponseDto availability;
            try {
                availability = merchantServiceClient.checkAvailability(
                        StockAvailabilityRequestDto.builder()
                                .merchantId(item.getMerchantId())
                                .variantId(item.getVariantId())
                                .quantity(item.getQuantity())
                                .build());
            } catch (ProductUnavailableException ex) {
                // No listing at all for this merchant+variant: the merchant delisted it.
                delisted.add(UnavailableCartItemDto.builder()
                        .cartItemId(item.getId())
                        .productId(item.getProductId())
                        .merchantId(item.getMerchantId())
                        .build());
                continue;
            }

            int availableStock = availability.getAvailableQuantity() == null ? 0 : availability.getAvailableQuantity();
            if (!availability.isAvailable() || availableStock < item.getQuantity()) {
                outOfStock.add(UnavailableCartItemDto.builder()
                        .cartItemId(item.getId())
                        .productId(item.getProductId())
                        .merchantId(item.getMerchantId())
                        .requestedQuantity(item.getQuantity())
                        .availableStock(availableStock)
                        .build());
                continue;
            }

            if (availability.getPrice().compareTo(item.getUnitPrice()) != 0) {
                priceChanged.add(ChangedPriceItemDto.builder()
                        .cartItemId(item.getId())
                        .oldPrice(item.getUnitPrice())
                        .currentPrice(availability.getPrice())
                        .build());
            }

            String productTitle = null;
            String productImage = null;
            try {
                ProductResponseDto product = productServiceClient.getProduct(item.getProductId());
                productTitle = product.getName();
                productImage = product.getThumbnail();
            } catch (DownstreamServiceUnavailableException | ProductNotFoundException ignored) {
                // Same best-effort degrade as GET cart: display fields only, never blocking.
            }

            BigDecimal lineTotal = availability.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            validItems.add(ValidatedCartItemDto.builder()
                    .cartItemId(item.getId())
                    .productId(item.getProductId())
                    .merchantId(item.getMerchantId())
                    .productTitle(productTitle)
                    .productImage(productImage)
                    .price(availability.getPrice())
                    .quantity(item.getQuantity())
                    .availableStock(availableStock)
                    .totalPrice(lineTotal)
                    .build());
        }

        // Priority order: delisted items block first, then stock, then price — the frontend
        // fixes one category and re-validates, surfacing the next category if any remains.
        if (!delisted.isEmpty()) {
            return CartValidationResponseDto.builder()
                    .valid(false)
                    .code("OFFER_UNAVAILABLE")
                    .message("Merchant is no longer selling one or more products in your cart")
                    .unavailableItems(delisted)
                    .build();
        }
        if (!outOfStock.isEmpty()) {
            return CartValidationResponseDto.builder()
                    .valid(false)
                    .code("INSUFFICIENT_STOCK")
                    .message("Some products have insufficient stock")
                    .unavailableItems(outOfStock)
                    .build();
        }
        if (!priceChanged.isEmpty()) {
            return CartValidationResponseDto.builder()
                    .valid(false)
                    .code("PRICE_CHANGED")
                    .message("Product price has changed")
                    .changedItems(priceChanged)
                    .build();
        }

        return CartValidationResponseDto.builder()
                .valid(true)
                .items(validItems)
                .subtotal(subtotal)
                .build();
    }

    @Override
    @Transactional
    public BuyNowResponseDto buyNow(UUID userId, AddCartItemRequestDto request) {
        CartResponseDto cart = addItemToCart(userId, request);

        UUID cartItemId = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(request.getProductId())
                        && item.getVariantId().equals(request.getVariantId())
                        && item.getMerchantId().equals(request.getMerchantId()))
                .map(CartItemResponseDto::getCartItemId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Item just added to cart could not be found"));

        return BuyNowResponseDto.builder()
                .cartItemId(cartItemId)
                .build();
    }

    @Override
    @Transactional
    public CheckoutResponseDto checkoutItem(UUID userId, UUID cartItemId, CheckoutAddressRequestDto customAddress) {
        UserDetailsResponse user = userServiceClient.getUser(userId);
        AddressResponseDto address = resolveAddress(userId, customAddress);

        Carts cart = requireActiveCart(userId);
        CartItems item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));
        if (!item.getCart().getCartId().equals(cart.getCartId())) {
            throw new CartItemNotFoundException(cartItemId);
        }

        List<ReserveStockRequestDto> reservedSoFar = new ArrayList<>();
        StockAvailabilityResponseDto availability;
        ProductResponseDto product;

        try {
            availability = merchantServiceClient.checkAvailability(
                    StockAvailabilityRequestDto.builder()
                            .merchantId(item.getMerchantId())
                            .variantId(item.getVariantId())
                            .quantity(item.getQuantity())
                            .build());
            requireSellable(availability, item.getProductId());

            ReserveStockRequestDto reserveRequest = ReserveStockRequestDto.builder()
                    .merchantId(item.getMerchantId())
                    .variantId(item.getVariantId())
                    .quantity(item.getQuantity())
                    .build();
            StockReservationResponseDto reservation = merchantServiceClient.reserveStock(reserveRequest);
            requireReserved(reservation, item.getProductId());
            reservedSoFar.add(reserveRequest);

            product = productServiceClient.getProduct(item.getProductId());
        } catch (InsufficientStockException | ProductUnavailableException | ProductNotFoundException
                 | DownstreamServiceUnavailableException ex) {
            releaseAll(reservedSoFar);
            throw ex;
        }

        BigDecimal lineTotal = availability.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

        OrderLineItemRequest orderLine = OrderLineItemRequest.builder()
                .cartItemId(item.getId())
                .productId(item.getProductId())
                .variantId(item.getVariantId())
                .merchantId(item.getMerchantId())
                .productName(product.getName())
                .imageUrl(product.getThumbnail())
                .quantity(item.getQuantity())
                .unitPrice(availability.getPrice())
                .lineTotal(lineTotal)
                .build();

        // This checks out just ONE item while the rest of the cart stays untouched, so the
        // real cart's own cartId can't be used here: Order Service allows only one order per
        // cartId ever, and a later full checkout of the remaining items would reuse that same
        // real cartId and get rejected as a duplicate. A fresh random UUID avoids that trap.
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .cartId(UUID.randomUUID())
                .userId(userId)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .shippingAddress(ShippingAddressRequest.builder()
                        .addressLine1(address.getAddressLine1())
                        .addressLine2(address.getAddressLine2())
                        .city(address.getCity())
                        .state(address.getState())
                        .country(address.getCountry())
                        .pincode(address.getPincode())
                        .build())
                .items(List.of(orderLine))
                .totalPrice(lineTotal)
                .build();

        OrderCreatedResponse orderResponse;
        try {
            orderResponse = orderServiceClient.createOrder(orderRequest);
        } catch (DownstreamServiceUnavailableException | OrderCreationFailedException | DuplicateOrderException ex) {
            releaseAll(reservedSoFar);
            throw ex;
        }

        // Only this one item is removed — the cart itself stays ACTIVE since other items may remain.
        cartItemRepository.delete(item);

        return CheckoutResponseDto.builder()
                .orderId(orderResponse.getId())
                .cartId(cart.getCartId())
                .userId(userId)
                .itemCount(item.getQuantity())
                .totalPrice(lineTotal)
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
     * When the checkout caller supplies their own address, it's saved as a new (non-default)
     * entry in their address book and used directly — no need to re-fetch it from User
     * Service afterward, since we already have every field from the request. Otherwise falls
     * back to whatever User Service has on file as their default.
     */
    private AddressResponseDto resolveAddress(UUID userId, CheckoutAddressRequestDto customAddress) {
        if (customAddress == null) {
            return userServiceClient.getDefaultAddress(userId);
        }

        userServiceClient.createAddress(userId, AddressRequestDto.builder()
                .addressLine1(customAddress.getAddressLine1())
                .addressLine2(customAddress.getAddressLine2())
                .city(customAddress.getCity())
                .state(customAddress.getState())
                .country(customAddress.getCountry())
                .pincode(customAddress.getPincode())
                .isDefault(false)
                .build());

        return AddressResponseDto.builder()
                .addressLine1(customAddress.getAddressLine1())
                .addressLine2(customAddress.getAddressLine2())
                .city(customAddress.getCity())
                .state(customAddress.getState())
                .country(customAddress.getCountry())
                .pincode(customAddress.getPincode())
                .build();
    }

    private void requireSellable(StockAvailabilityResponseDto availability, String productId) {
        if (!availability.isAvailable()) {
            int availableQuantity = availability.getAvailableQuantity() == null ? 0 : availability.getAvailableQuantity();
            throw new InsufficientStockException(productId, availability.getRequestedQuantity(), availableQuantity);
        }
    }

    private void requireReserved(StockReservationResponseDto reservation, String productId) {
        if (!reservation.isSuccess()) {
            int remaining = reservation.getRemainingStock() == null ? 0 : reservation.getRemainingStock();
            throw new InsufficientStockException(productId, reservation.getQuantity(), remaining);
        }
    }

    private void releaseAll(List<ReserveStockRequestDto> reserved) {
        for (ReserveStockRequestDto r : reserved) {
            try {
                merchantServiceClient.releaseStock(r);
            } catch (RuntimeException ignored) {
                // Best-effort compensation: one failed release must not block releasing the
                // rest, and must not mask the original failure that triggered this rollback.
            }
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

        boolean available = false;
        try {
            StockAvailabilityResponseDto availability = merchantServiceClient.checkAvailability(
                    StockAvailabilityRequestDto.builder()
                            .merchantId(item.getMerchantId())
                            .variantId(item.getVariantId())
                            .quantity(item.getQuantity())
                            .build());
            available = availability.isAvailable();
        } catch (DownstreamServiceUnavailableException ignored) {
            // Merchant Service is down/unreachable: show the cart with stored data, degrade display fields only.
        }

        String productName = null;
        String productImage = null;
        try {
            ProductResponseDto product = productServiceClient.getProduct(item.getProductId());
            productName = product.getName();
            productImage = product.getThumbnail();
        } catch (DownstreamServiceUnavailableException | ProductNotFoundException ignored) {
            // Product Service is down, or this productId no longer exists there: degrade display only.
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
