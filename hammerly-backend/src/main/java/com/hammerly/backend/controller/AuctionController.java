package com.hammerly.backend.controller;

import com.hammerly.backend.dto.AuctionDtos.CreateAuctionRequest;
import com.hammerly.backend.security.AuthenticatedUser;
import com.hammerly.backend.service.AuctionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auctions")
public class AuctionController {
    private final AuctionService service;

    public AuctionController(AuctionService service) {
        this.service = service;
    }

    @GetMapping("/get-top")
    @Operation(summary = "Get top auctions", tags = "Auctions")
    Map<String, Object> top() {
        return service.top();
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "Get auction details", tags = "Auctions")
    Map<String, Object> get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping("/get-related/{id}")
    @Operation(summary = "Get related auctions", tags = "Auctions")
    Map<String, Object> related(@PathVariable String id) {
        return service.related(id);
    }

    @GetMapping("/search")
    @Operation(summary = "Search auctions", tags = "Auctions")
    Map<String, Object> search(@RequestParam(required = false, defaultValue = "") String q,
                               @RequestParam(required = false, defaultValue = "1") String page,
                               @RequestParam(required = false, defaultValue = "12") String size) {
        return service.search(q, page, size);
    }

    @GetMapping({"/bid/{id}", "/{id}/bid"})
    @Operation(summary = "Place a bid", tags = "Auctions", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> bid(@PathVariable String id,
                            @RequestParam(required = false) String bidAmount,
                            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.placeBid(id, bidAmount, user.userId());
    }

    @PostMapping("/watch/{id}")
    @Operation(summary = "Watch an auction", tags = "Auctions", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> watch(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.watch(id, user.userId());
    }

    @DeleteMapping("/unwatch/{id}")
    @Operation(summary = "Unwatch an auction", tags = "Auctions", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> unwatch(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.unwatch(id, user.userId());
    }

    @GetMapping("/get-watchlist")
    @Operation(summary = "Get current user's watchlist", tags = "Auctions", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> watchlist(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.watchlist(user.userId());
    }

    @GetMapping("/is-watched/{id}")
    @Operation(summary = "Check watch status", tags = "Auctions", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> isWatched(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.isWatched(id, user.userId());
    }

    @PostMapping("/create")
    @Operation(summary = "Create an auction", tags = "Auctions", security = @SecurityRequirement(name = "BearerAuth"))
    ResponseEntity<Map<String, Object>> create(@RequestBody CreateAuctionRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, user.userId()));
    }

    @PatchMapping("/end/{id}")
    @Operation(summary = "End an auction", tags = "Auctions", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> end(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.end(id, user.userId());
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "Delete an auction", tags = "Auctions", security = @SecurityRequirement(name = "BearerAuth"))
    Map<String, Object> delete(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.delete(id, user.userId());
    }
}
