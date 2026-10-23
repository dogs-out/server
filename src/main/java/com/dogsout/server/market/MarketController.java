package com.dogsout.server.market;

import com.dogsout.server.market.MarketDtos.ConversationSummary;
import com.dogsout.server.market.MarketDtos.ListingRequest;
import com.dogsout.server.market.MarketDtos.ListingResponse;
import com.dogsout.server.market.MarketDtos.MessageRequest;
import com.dogsout.server.market.MarketDtos.MessageResponse;
import com.dogsout.server.market.MarketDtos.StatusRequest;
import com.dogsout.server.moderation.ReportRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService market;

    @GetMapping
    public List<ListingResponse> browse(Authentication auth, @RequestParam(required = false) String category) {
        return market.browse(auth.getName(), category);
    }

    @GetMapping("/mine")
    public List<ListingResponse> mine(Authentication auth) {
        return market.mine(auth.getName());
    }

    @GetMapping("/{id}")
    public ListingResponse get(Authentication auth, @PathVariable Long id) {
        return market.get(auth.getName(), id);
    }

    @PostMapping
    public ResponseEntity<ListingResponse> create(Authentication auth, @Valid @RequestBody ListingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(market.create(auth.getName(), request));
    }

    @PutMapping("/{id}")
    public ListingResponse update(Authentication auth, @PathVariable Long id, @Valid @RequestBody ListingRequest request) {
        return market.update(auth.getName(), id, request);
    }

    @PutMapping("/{id}/status")
    public ListingResponse setStatus(Authentication auth, @PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return market.setStatus(auth.getName(), id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        market.delete(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ListingResponse addPhoto(Authentication auth, @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return market.addPhoto(auth.getName(), id, file);
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ListingResponse deletePhoto(Authentication auth, @PathVariable Long id, @PathVariable Long photoId) {
        return market.deletePhoto(auth.getName(), id, photoId);
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<Void> report(Authentication auth, @PathVariable Long id, @Valid @RequestBody ReportRequest request) {
        market.report(auth.getName(), id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/conversation")
    public ConversationSummary openConversation(Authentication auth, @PathVariable Long id) {
        return market.openConversation(auth.getName(), id);
    }

    @GetMapping("/conversations")
    public List<ConversationSummary> conversations(Authentication auth) {
        return market.conversations(auth.getName());
    }

    @GetMapping("/conversations/{id}")
    public ConversationSummary conversation(Authentication auth, @PathVariable Long id) {
        return market.conversation(auth.getName(), id);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageResponse> messages(Authentication auth, @PathVariable Long id) {
        return market.messages(auth.getName(), id);
    }

    @PostMapping("/conversations/{id}/messages")
    public MessageResponse send(Authentication auth, @PathVariable Long id, @Valid @RequestBody MessageRequest request) {
        return market.send(auth.getName(), id, request.content());
    }
}
