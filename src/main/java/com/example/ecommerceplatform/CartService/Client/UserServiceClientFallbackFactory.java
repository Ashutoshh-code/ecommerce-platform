package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.response.AddressResponseDto;
import com.example.ecommerceplatform.CartService.Dto.response.UserDetailsResponse;
import com.example.ecommerceplatform.CartService.Exception.AddressNotFoundException;
import com.example.ecommerceplatform.CartService.Exception.DownstreamServiceUnavailableException;
import com.example.ecommerceplatform.CartService.Exception.UserNotFoundException;
import feign.FeignException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * A 404 means "no such user" or "no default address" — a real, meaningful answer, not
 * an outage. Everything else means User Service is genuinely unreachable.
 */
@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {
            @Override
            public UserDetailsResponse getUser(UUID userId) {
                if (cause instanceof FeignException fe && fe.status() == 404) {
                    throw new UserNotFoundException(userId);
                }
                throw new DownstreamServiceUnavailableException("User Service", cause);
            }

            @Override
            public AddressResponseDto getDefaultAddress(UUID userId) {
                if (cause instanceof FeignException fe && fe.status() == 404) {
                    throw new AddressNotFoundException(userId);
                }
                throw new DownstreamServiceUnavailableException("User Service", cause);
            }
        };
    }
}
