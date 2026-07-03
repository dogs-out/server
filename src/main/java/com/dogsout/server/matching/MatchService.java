package com.dogsout.server.matching;

import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final UserRepository userRepository;

    public SwipeResponse swipe(String email, SwipeRequest request) {
        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        User target = userRepository.findById(request.targetUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found"));

        if (me.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot swipe on yourself");
        }

        boolean isLike = "LIKE".equalsIgnoreCase(request.action());

        if (isLike) {
            Optional<Match> theirLike = matchRepository.findByUser1AndUser2(target, me);
            if (theirLike.isPresent() && theirLike.get().getStatus() == MatchStatus.PENDING) {
                Match mutual = theirLike.get();
                mutual.setStatus(MatchStatus.MATCHED);
                matchRepository.save(mutual);
                return new SwipeResponse(true, mutual.getId());
            }
        }

        Match swipe = new Match();
        swipe.setUser1(me);
        swipe.setUser2(target);
        swipe.setStatus(isLike ? MatchStatus.PENDING : MatchStatus.REJECTED);
        Match saved = matchRepository.save(swipe);

        return new SwipeResponse(false, saved.getId());
    }
}
