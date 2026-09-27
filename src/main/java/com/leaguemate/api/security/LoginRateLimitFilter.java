package com.leaguemate.api.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final long capacity;
    private final Duration period;

    public LoginRateLimitFilter(@Value("${security.login-rate-limit.capacity:10}") long capacity,
                                @Value("${security.login-rate-limit.period:1m}") Duration period) {
        this.capacity = capacity;
        this.period = period;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(HttpMethod.POST.matches(request.getMethod()) && LOGIN_PATH.equals(path));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), key -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            SecurityErrorResponse.write(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Retry in " + retryAfterSeconds + " seconds");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Scheduled(fixedDelayString = "${security.login-rate-limit.cleanup-interval:10m}")
    public int evictIdleBuckets() {
        int before = buckets.size();
        buckets.values().removeIf(bucket -> bucket.getAvailableTokens() >= capacity);
        return before - buckets.size();
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
                .build();
    }
}
