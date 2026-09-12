package com.dogsout.server.user;

import java.util.List;

/**
 * Who to tell that you are out.
 *
 * @param userIds empty or absent for every match; otherwise only these, and only
 *                those among them who are actually matched — the list narrows the
 *                audience, it can never widen it.
 */
public record InviteToWalkRequest(List<Long> userIds) {}
