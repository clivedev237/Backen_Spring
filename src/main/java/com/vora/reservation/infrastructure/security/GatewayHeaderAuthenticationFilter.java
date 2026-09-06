package com.vora.reservation.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Le JWT est émis et validé par le Gateway (mode délégué, confirmé). Ce
 * filtre ne fait AUCUNE validation de token : il fait uniquement confiance
 * aux headers d'identité posés par le Gateway en amont
 * (X-User-Id, X-User-Role, X-Driver-Id), conformément au cadrage §14.
 *
 * En environnement de développement sans Gateway devant le service, ces
 * headers peuvent être positionnés manuellement (Postman, tests d'intégration).
 */
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_DRIVER_ID = "X-Driver-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String userIdHeader = request.getHeader(HEADER_USER_ID);
        String roleHeader = request.getHeader(HEADER_USER_ROLE);

        if (userIdHeader != null && roleHeader != null) {
            try {
                Long userId = Long.parseLong(userIdHeader);
                VoraRole role = VoraRole.valueOf(roleHeader.toUpperCase());
                Long driverId = null;
                String driverIdHeader = request.getHeader(HEADER_DRIVER_ID);
                if (driverIdHeader != null && !driverIdHeader.isBlank()) {
                    driverId = Long.parseLong(driverIdHeader);
                }

                AuthenticatedUser principal = new AuthenticatedUser(userId, role, driverId);
                List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));

                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (IllegalArgumentException e) {
                // Header malformé : on laisse la requête anonyme, elle sera rejetée
                // par les règles d'autorisation si l'endpoint le requiert.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
