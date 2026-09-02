package com.hmdp.service;

public interface ISeckillAccessTokenService {

    boolean isEnabled();

    String issueAccessToken(Long voucherId, Long userId);
}
