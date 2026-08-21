package com.hammerly.backend.service;

import com.hammerly.backend.dto.UserDtos.AddPaymentMethodRequest;
import com.hammerly.backend.dto.UserDtos.ChangePasswordRequest;
import com.hammerly.backend.dto.UserDtos.UpdateProfileRequest;
import com.hammerly.backend.exception.ApiException;
import com.hammerly.backend.model.PaymentMethod;
import com.hammerly.backend.model.User;
import com.hammerly.backend.repository.AuctionRepository;
import com.hammerly.backend.repository.BidRepository;
import com.hammerly.backend.repository.PaymentMethodRepository;
import com.hammerly.backend.repository.UserRepository;
import com.hammerly.backend.util.TimeUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final PaymentMethodRepository paymentMethods;
    private final BidRepository bids;
    private final AuctionRepository auctions;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PaymentMethodRepository paymentMethods,
                       BidRepository bids, AuctionRepository auctions, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.paymentMethods = paymentMethods;
        this.bids = bids;
        this.auctions = auctions;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> profile(long userId) {
        User user = requireUser(userId);
        Map<String, Object> response = success();
        response.put("user", mapProfile(user));
        return response;
    }

    public Map<String, Object> updateProfile(long userId, UpdateProfileRequest request) {
        if (request == null || missing(request.firstName()) || missing(request.lastName()) || missing(request.email())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "First name, last name, and email are required");
        }
        if (users.emailBelongsToAnotherUser(request.email(), userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already in use by another account");
        }
        users.updateProfile(userId, request.firstName(), request.lastName(), request.email(),
            request.phone() == null ? "" : request.phone());
        Map<String, Object> response = successMessage("Profile updated successfully");
        response.put("user", mapProfile(requireUser(userId)));
        return response;
    }

    public Map<String, Object> changePassword(long userId, ChangePasswordRequest request) {
        if (request == null || missing(request.currentPassword()) || missing(request.newPassword()) ||
            missing(request.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "All password fields are required");
        }
        if (request.newPassword().length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must be at least 6 characters");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New passwords do not match");
        }
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.password())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        users.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
        return successMessage("Password updated successfully");
    }

    public Map<String, Object> updateAvatar(long userId, String avatarImage) {
        if (missing(avatarImage)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Avatar image is required");
        }
        users.updateAvatar(userId, avatarImage);
        Map<String, Object> response = successMessage("Avatar updated successfully");
        response.put("avatarImage", avatarImage);
        return response;
    }

    public Map<String, Object> removeAvatar(long userId) {
        users.updateAvatar(userId, "");
        return successMessage("Avatar removed successfully");
    }

    public Map<String, Object> paymentMethods(long userId) {
        Map<String, Object> response = success();
        response.put("paymentMethods", paymentMethods.findByUser(userId).stream().map(this::mapPayment).toList());
        return response;
    }

    @Transactional
    public Map<String, Object> addPaymentMethod(long userId, AddPaymentMethodRequest request) {
        String sanitized = request == null || request.cardNumber() == null
            ? "" : request.cardNumber().replaceAll("\\D", "");
        if (request == null || missing(request.cardType()) || sanitized.isEmpty() ||
            request.expiryMonth() == null || request.expiryMonth() == 0 ||
            request.expiryYear() == null || request.expiryYear() == 0 || missing(request.cardholderName())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                "Card type, card number, expiry, and cardholder name are required");
        }
        if (!sanitized.matches("\\d{12,19}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Card number must be between 12 and 19 digits");
        }
        boolean requestedDefault = Boolean.TRUE.equals(request.isDefault());
        if (requestedDefault) paymentMethods.clearDefault(userId);
        int makeDefault = paymentMethods.countByUser(userId) == 0 || requestedDefault ? 1 : 0;
        long id = paymentMethods.insert(userId, request.cardType(), sanitized,
            sanitized.substring(sanitized.length() - 4), request.expiryMonth(), request.expiryYear(),
            request.cardholderName(), makeDefault, valueOrEmpty(request.billingAddress()),
            valueOrEmpty(request.billingCity()), valueOrEmpty(request.billingProvince()),
            valueOrEmpty(request.billingPostalCode()), valueOrEmpty(request.billingCountry()));
        PaymentMethod created = paymentMethods.findOwned(id, userId).orElseThrow();
        Map<String, Object> response = successMessage("Payment method added successfully");
        response.put("paymentMethod", mapPayment(created));
        return response;
    }

    @Transactional
    public Map<String, Object> deletePaymentMethod(long userId, String rawId) {
        long id = parsePaymentMethodId(rawId);
        PaymentMethod method = paymentMethods.findOwned(id, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment method not found"));
        paymentMethods.delete(id, userId);
        if (method.isDefault() != 0) {
            paymentMethods.newestId(userId).ifPresent(paymentMethods::setDefault);
        }
        return successMessage("Payment method deleted");
    }

    @Transactional
    public Map<String, Object> setDefaultPaymentMethod(long userId, String rawId) {
        long id = parsePaymentMethodId(rawId);
        paymentMethods.findOwned(id, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment method not found"));
        paymentMethods.clearDefault(userId);
        paymentMethods.setDefault(id);
        return successMessage("Default payment method updated");
    }

    public Map<String, Object> myBids(long userId) {
        List<Map<String, Object>> mapped = bids.findByBidder(userId).stream().map(bid -> {
            double yourBid = bid.yourBid();
            double currentBid = bid.currentBid();
            boolean ended = TimeUtils.hasEnded(bid.endTime());
            boolean highest = yourBid >= currentBid;
            String status = ended ? (highest ? "won" : "lost") : (highest ? "winning" : "outbid");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", bid.auctionId());
            item.put("title", bid.title());
            item.put("description", valueOrEmpty(bid.description()));
            item.put("image", truthy(bid.image()) ? bid.image() : "/images/picture.jpg");
            item.put("timeLeft", TimeUtils.timeRemaining(bid.endTime()));
            item.put("yourBid", yourBid);
            item.put("currentBid", currentBid);
            item.put("totalBids", bid.totalBids());
            item.put("status", status);
            item.put("sellerName", truthy(bid.sellerName()) ? bid.sellerName() : "Unknown Seller");
            item.put("sellerAvatar", truthy(bid.sellerAvatar()) ? bid.sellerAvatar() : "/images/user.jpg");
            item.put("purchasePrice", "won".equals(status) ? currentBid : yourBid);
            item.put("orderDate", TimeUtils.localDate(bid.endTime()));
            item.put("deliverStatus", "won".equals(status) ? "confirmed" : null);
            return item;
        }).toList();
        Map<String, Object> response = success();
        response.put("bids", mapped);
        return response;
    }

    public Map<String, Object> myAuctions(long userId) {
        List<Map<String, Object>> mapped = auctions.findBySeller(userId).stream().map(auction -> {
            String status = listingStatus(auction.status(), auction.endTime());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", auction.id());
            item.put("title", auction.title());
            item.put("description", valueOrEmpty(auction.description()));
            item.put("startingPrice", auction.startPrice());
            item.put("currentBid", auction.currentBid());
            item.put("bids", auction.totalBids());
            item.put("watchers", auction.totalWatchers());
            item.put("timeLeft", "draft".equals(status) ? "Draft" : "ended".equals(status)
                ? "Ended" : TimeUtils.timeRemaining(auction.endTime()));
            item.put("status", status);
            item.put("image", truthy(auction.image()) ? auction.image() : "/images/picture.jpg");
            item.put("createdAt", valueOrEmpty(auction.createdAt()));
            return item;
        }).toList();
        Map<String, Object> response = success();
        response.put("auctions", mapped);
        return response;
    }

    public Map<String, Object> publicUser(String rawId) {
        long id = parseUserId(rawId);
        User user = users.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        Map<String, Object> publicUser = new LinkedHashMap<>();
        publicUser.put("id", user.id());
        publicUser.put("firstName", user.firstName());
        publicUser.put("lastName", user.lastName());
        publicUser.put("avatarImage", user.avatarImage());
        publicUser.put("createdAt", user.createdAt());
        Map<String, Object> response = success();
        response.put("user", publicUser);
        return response;
    }

    private User requireUser(long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Map<String, Object> mapProfile(User user) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("id", user.id());
        mapped.put("firstName", user.firstName());
        mapped.put("lastName", user.lastName());
        mapped.put("email", user.email());
        mapped.put("phone", user.phone());
        mapped.put("avatarImage", user.avatarImage());
        mapped.put("createdAt", user.createdAt());
        return mapped;
    }

    private Map<String, Object> mapPayment(PaymentMethod method) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("id", method.id());
        mapped.put("user_id", method.userId());
        mapped.put("cardType", method.cardType());
        mapped.put("cardNumber", method.cardNumber());
        mapped.put("expiryMonth", method.expiryMonth());
        mapped.put("expiryYear", method.expiryYear());
        mapped.put("cardholderName", method.cardholderName());
        mapped.put("isDefault", method.isDefault());
        mapped.put("billingAddress", method.billingAddress());
        mapped.put("billingCity", method.billingCity());
        mapped.put("billingProvince", method.billingProvince());
        mapped.put("billingPostalCode", method.billingPostalCode());
        mapped.put("billingCountry", method.billingCountry());
        mapped.put("createdAt", method.createdAt());
        return mapped;
    }

    private String listingStatus(String status, String endTime) {
        if ("draft".equals(status)) return "draft";
        if ("ended".equals(status)) return "ended";
        if ("activate".equals(status) || "active".equals(status)) {
            if (endTime == null) return "active";
            return TimeUtils.hasEnded(endTime) ? "ended" : "active";
        }
        if (endTime == null) return "draft";
        return TimeUtils.hasEnded(endTime) ? "ended" : "active";
    }

    private long parsePaymentMethodId(String rawId) {
        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Payment method not found");
        }
    }

    private long parseUserId(String rawId) {
        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private Map<String, Object> success() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        return response;
    }

    private Map<String, Object> successMessage(String message) {
        Map<String, Object> response = success();
        response.put("message", message);
        return response;
    }

    private boolean missing(String value) {
        return value == null || value.isEmpty();
    }

    private boolean truthy(String value) {
        return value != null && !value.isEmpty();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
