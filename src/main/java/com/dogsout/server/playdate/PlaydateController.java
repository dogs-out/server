package com.dogsout.server.playdate;

import com.dogsout.server.playdate.PlaydateDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/playdates")
@RequiredArgsConstructor
public class PlaydateController {

    private final PlaydateService playdateService;

    @GetMapping
    public ResponseEntity<List<PlaydateResponse>> getFeed(Authentication auth) {
        return ResponseEntity.ok(playdateService.getFeed(auth.getName()));
    }

    @PostMapping
    public ResponseEntity<PlaydateResponse> create(Authentication auth,
                                                   @Valid @RequestBody CreatePlaydateRequest request) {
        return ResponseEntity.ok(playdateService.create(auth.getName(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlaydateResponse> get(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(playdateService.getPlaydate(auth.getName(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlaydateResponse> update(Authentication auth, @PathVariable Long id,
                                                   @Valid @RequestBody UpdatePlaydateRequest request) {
        return ResponseEntity.ok(playdateService.update(auth.getName(), id, request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(Authentication auth, @PathVariable Long id) {
        playdateService.cancel(auth.getName(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<PlaydateResponse> join(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(playdateService.join(auth.getName(), id));
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leave(Authentication auth, @PathVariable Long id) {
        playdateService.leave(auth.getName(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/invites")
    public ResponseEntity<PlaydateResponse> invite(Authentication auth, @PathVariable Long id,
                                                   @Valid @RequestBody InviteRequest request) {
        return ResponseEntity.ok(playdateService.invite(auth.getName(), id, request.userIds()));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<PlaydateMessageResponse>> getMessages(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(playdateService.getMessages(auth.getName(), id));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<PlaydateMessageResponse> sendMessage(Authentication auth, @PathVariable Long id,
                                                               @Valid @RequestBody SendPlaydateMessageRequest request) {
        return ResponseEntity.ok(playdateService.sendMessage(auth.getName(), id, request.content()));
    }
}
