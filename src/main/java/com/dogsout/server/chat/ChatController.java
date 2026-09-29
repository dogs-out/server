package com.dogsout.server.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/{matchId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(Authentication auth, @PathVariable Long matchId) {
        return ResponseEntity.ok(chatService.getMessages(auth.getName(), matchId));
    }

    @PostMapping("/{matchId}/walk-invite")
    public ResponseEntity<MessageResponse> sendWalkInvite(Authentication auth, @PathVariable Long matchId,
                                                          @Valid @RequestBody com.dogsout.server.playdate.PlaydateDtos.WalkInviteRequest request) {
        return ResponseEntity.ok(chatService.sendWalkInvite(auth.getName(), matchId, request));
    }

    @PostMapping("/{matchId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(Authentication auth, @PathVariable Long matchId,
                                                       @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(chatService.sendMessage(auth.getName(), matchId, request));
    }
}
