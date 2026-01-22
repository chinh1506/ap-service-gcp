package com.cyberlogitec.ap_service_gcp.configuration.security;

import com.cyberlogitec.ap_service_gcp.service.DriveServiceHelper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GoogleTokenFilter extends OncePerRequestFilter {

    @Value("${app.pubsub.invoker-email}")
    private String pubSubEmail;

    // Giả sử bạn đã có Service check quyền Drive (có Caching) như đã bàn ở trên
    private final DriveServiceHelper driveServiceHelper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        String workFileId = request.getHeader("X-Work-File-Id");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            // 1. Verify Token với Google
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .build();
            GoogleIdToken idToken = null;
            try {
                idToken = verifier.verify(token);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }

            if (idToken != null) {
                String email = idToken.getPayload().getEmail();
                List<SimpleGrantedAuthority> authorities;

                // 2. Phân quyền (Authorization Logic)
                if (email.equals(pubSubEmail)) {
                    // Case A: Là Pub/Sub Service Account
                    authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_SYSTEM_WORKER"));
                } else {
                    // Case B: Là User thật -> Check quyền Drive (Nên dùng Cache ở đây)
                    // Lấy workFileId từ Header hoặc Body nếu cần check chi tiết,
                    // hoặc check quyền Manager chung.
                        boolean isManager = driveServiceHelper.isManagerInDrive(email,workFileId);

                    if (isManager) {
                        authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_MANAGER"));
                    } else {
                        authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
                    }
                }

                // 3. Tạo Authentication Object và đưa vào Context
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(email, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

        }

        filterChain.doFilter(request, response);
    }
}
