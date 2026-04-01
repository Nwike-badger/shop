package semicolon.africa.waylchub.service.productService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semicolon.africa.waylchub.exception.ResourceNotFoundException;
import semicolon.africa.waylchub.model.product.Product;
import semicolon.africa.waylchub.model.product.Wishlist;
import semicolon.africa.waylchub.model.product.WishlistItem;
import semicolon.africa.waylchub.repository.productRepository.ProductRepository;
import semicolon.africa.waylchub.repository.productRepository.WishlistRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private static final int MAX_WISHLIST_ITEMS = 100;

    @Transactional
    public Wishlist addToWishlist(String userId, String sessionId, String productId) {
        Wishlist wishlist = getWishlist(userId, sessionId);

        if (wishlist == null) {
            wishlist = Wishlist.builder()
                    .userId(userId)
                    .sessionId(userId == null ? sessionId : null)
                    .items(new ArrayList<>())
                    .build();
        }

        // Idempotency: if it's already there, just return
        boolean alreadyExists = wishlist.getItems().stream()
                .anyMatch(i -> i.getProductId().equals(productId));
        if (alreadyExists) return wishlist;

        if (wishlist.getItems().size() >= MAX_WISHLIST_ITEMS) {
            throw new IllegalArgumentException("Wishlist is full (max " + MAX_WISHLIST_ITEMS + " items)");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (!product.isActive()) {
            throw new IllegalArgumentException("Cannot add inactive product to wishlist");
        }

        String imageUrl = (product.getImages() != null && !product.getImages().isEmpty())
                ? product.getImages().get(0).getUrl() : null;

        WishlistItem newItem = WishlistItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .slug(product.getSlug())
                .categorySlug(product.getCategorySlug())
                .brandName(product.getBrandName())
                .imageUrl(imageUrl)
                .currentPrice(product.getMinPrice() != null ? product.getMinPrice() : product.getBasePrice())
                .compareAtPrice(product.getCompareAtPrice())
                .build();

        wishlist.getItems().add(0, newItem); // Add to top of list
        wishlist.setUpdatedAt(LocalDateTime.now());
        return wishlistRepository.save(wishlist);
    }

    @Transactional
    public Wishlist removeFromWishlist(String userId, String sessionId, String productId) {
        Wishlist wishlist = getWishlist(userId, sessionId);
        if (wishlist == null) throw new ResourceNotFoundException("Wishlist not found");

        wishlist.getItems().removeIf(item -> item.getProductId().equals(productId));
        wishlist.setUpdatedAt(LocalDateTime.now());
        return wishlistRepository.save(wishlist);
    }

    @Transactional
    public void clearWishlist(String userId, String sessionId) {
        Wishlist wishlist = getWishlist(userId, sessionId);
        if (wishlist != null) {
            wishlist.getItems().clear();
            wishlistRepository.save(wishlist);
        }
    }

    @Transactional
    public void mergeWishlists(String guestSessionId, String userId) {
        if (guestSessionId == null || userId == null) return;

        Optional<Wishlist> guestWishlistOpt = wishlistRepository.findBySessionId(guestSessionId);
        Optional<Wishlist> userWishlistOpt = wishlistRepository.findByUserId(userId);

        if (guestWishlistOpt.isEmpty()) return;
        Wishlist guestWishlist = guestWishlistOpt.get();

        if (userWishlistOpt.isPresent()) {
            Wishlist userWishlist = userWishlistOpt.get();
            log.info("Merging guest wishlist {} into user wishlist {}", guestSessionId, userId);

            for (WishlistItem guestItem : guestWishlist.getItems()) {
                boolean alreadyInUserList = userWishlist.getItems().stream()
                        .anyMatch(i -> i.getProductId().equals(guestItem.getProductId()));

                if (!alreadyInUserList && userWishlist.getItems().size() < MAX_WISHLIST_ITEMS) {
                    userWishlist.getItems().add(guestItem);
                }
            }

            userWishlist.setUpdatedAt(LocalDateTime.now());
            wishlistRepository.save(userWishlist);
            wishlistRepository.delete(guestWishlist);

        } else {
            log.info("Assigning guest wishlist {} to user {}", guestSessionId, userId);
            guestWishlist.setUserId(userId);
            guestWishlist.setSessionId(null);
            guestWishlist.setUpdatedAt(LocalDateTime.now());
            wishlistRepository.save(guestWishlist);
        }
    }

    public Wishlist getWishlist(String userId, String sessionId) {
        if (userId != null) {
            return wishlistRepository.findByUserId(userId).orElse(null);
        }
        return wishlistRepository.findBySessionId(sessionId).orElse(null);
    }
}