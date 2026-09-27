package com.leaguemate.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginRateLimitFilterTest {

    private static void login(LoginRateLimitFilter filter, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void evictIdleBuckets_KeepsBucketsStillInUse() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(2, Duration.ofMinutes(1));
        login(filter, "10.0.0.1");

        assertEquals(0, filter.evictIdleBuckets());
    }

    @Test
    void evictIdleBuckets_RemovesBucketsThatRefilled() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(2, Duration.ofMillis(20));
        login(filter, "10.0.0.1");
        login(filter, "10.0.0.2");

        Thread.sleep(100);

        assertEquals(2, filter.evictIdleBuckets());
        assertEquals(0, filter.evictIdleBuckets());
    }
}
