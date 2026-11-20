package com.financeapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public SecurityConfig(UserDetailsService userDetailsService,
                          PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/ws/**", "/api/**", "/register/**", "/login", "/send-code", "/resend-code", "/send-otp")
            )
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/login", "/login/**",
                    "/register", "/register/**",
                    "/api/auth/**",
                    "/api/auth/check-password-reuse",
                    "/api/auth/check-username",
                    "/send-code", "/resend-code", "/send-otp",
                    "/css/**", "/js/**", "/images/**",
                    "/favicon.ico",
                    "/error",
                    "/ws/**"
                ).permitAll()
                .requestMatchers("/employee/portal", "/employee/portal/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN", "ROLE_EMPLOYEE", "ROLE_SECURITY", "ROLE_EMPLOYEE_PENDING")
                .requestMatchers("/admin/security", "/admin/security/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN", "ROLE_EMPLOYEE", "ROLE_SECURITY")
                .requestMatchers("/admin/archive", "/admin/archive/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN", "ROLE_EMPLOYEE", "ROLE_SECURITY")
                .requestMatchers("/admin/accounts", "/admin/accounts/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN", "ROLE_EMPLOYEE", "ROLE_SECURITY")
                .requestMatchers("/admin/inquiry", "/admin/inquiry/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN", "ROLE_EMPLOYEE", "ROLE_SECURITY")
                .requestMatchers("/admin/treasury", "/admin/treasury/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN", "ROLE_EMPLOYEE", "ROLE_SECURITY")
                .requestMatchers("/admin/loans", "/admin/loans/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN", "ROLE_EMPLOYEE", "ROLE_SECURITY")
                .requestMatchers("/admin", "/admin/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                .requestMatchers(
                    "/dashboard", "/dashboard/**",
                    "/card", "/card/**",
                    "/account",  "/account/**",
                    "/employee/card",
                    "/appeals", "/appeals/**",
                    "/loan", "/loan/**"
                ).authenticated()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler((request, response, authentication) -> {
                    boolean isAdmin = authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN")
                                        || a.getAuthority().equalsIgnoreCase("ADMIN"));
                    boolean isEmployee = authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_EMPLOYEE")
                                        || a.getAuthority().equalsIgnoreCase("ROLE_SECURITY")
                                        || a.getAuthority().equalsIgnoreCase("ROLE_EMPLOYEE_PENDING"));
                    if (isAdmin) {
                        response.sendRedirect("/admin");
                    } else if (isEmployee) {
                        response.sendRedirect("/employee/portal");
                    } else {
                        response.sendRedirect("/dashboard");
                    }
                })
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendRedirect("/login")
                )
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    response.sendRedirect("/login?accessDenied=true")
                )
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }
}
