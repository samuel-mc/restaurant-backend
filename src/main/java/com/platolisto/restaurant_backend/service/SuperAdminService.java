package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.billing.EstimatedMrrCalculator;
import com.platolisto.restaurant_backend.billing.SubscriptionPeriodSupport;
import com.platolisto.restaurant_backend.dto.LoginRequest;
import com.platolisto.restaurant_backend.dto.LoginResponse;
import com.platolisto.restaurant_backend.dto.superadmin.ImpersonateResponse;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminMetricsResponse;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminTenantResponse;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminTenantSubscriptionRequest;
import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import com.platolisto.restaurant_backend.entity.User;
import com.platolisto.restaurant_backend.entity.UserRole;
import com.platolisto.restaurant_backend.plan.PlanLimits;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.repository.UserRepository;
import com.platolisto.restaurant_backend.security.ImpersonationHandoffService;
import com.platolisto.restaurant_backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ImpersonationHandoffService impersonationHandoffService;
    private final UserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;
    private final EstimatedMrrCalculator estimatedMrrCalculator;

    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado."));

        if (user.getRole() != UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Esta cuenta no tiene acceso al backoffice global.");
        }
        if (!user.isActive()) {
            throw new IllegalArgumentException("La cuenta de superadmin está desactivada.");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails, null, UserRole.SUPER_ADMIN.name());

        log.info("SuperAdmin login: {}", email);
        return LoginResponse.builder().token(token).build();
    }

    @Transactional(readOnly = true)
    public List<SuperAdminTenantResponse> listTenants() {
        return restaurantRepository.findAll().stream()
                .sorted(Comparator.comparing(Restaurant::getCreatedAt).reversed())
                .map(this::toTenantResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public SuperAdminTenantResponse updateTenantStatus(Long id, boolean active) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Restaurante no encontrado."));
        restaurant.setActive(active);
        Restaurant saved = restaurantRepository.save(restaurant);
        log.info("Tenant {} marcado active={}", saved.getSubdomain(), active);
        return toTenantResponse(saved);
    }

    @Transactional
    public SuperAdminTenantResponse updateTenantSubscription(
            Long id,
            SuperAdminTenantSubscriptionRequest request,
            String actorEmail
    ) {
        String actor = actorEmail != null ? actorEmail.trim().toLowerCase(Locale.ROOT) : "unknown";
        SubscriptionPlan plan = request.getPlan();
        PaymentStatus paymentStatus = request.getPaymentStatus();
        if (plan != SubscriptionPlan.BASIC && plan != SubscriptionPlan.PRO) {
            throw new IllegalArgumentException("Plan no válido. Elige BASIC o PRO.");
        }
        if (paymentStatus != PaymentStatus.ACTIVE && paymentStatus != PaymentStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException("Estado de pago no válido.");
        }

        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Restaurante no encontrado."));

        SubscriptionPlan previousPlan = restaurant.getPlan();
        PaymentStatus previousPayment = restaurant.getPaymentStatus();

        restaurant.setPlan(plan);
        restaurant.setPaymentStatus(paymentStatus);
        if (PlanLimits.canPublishWebsite(plan, paymentStatus)) {
            restaurant.setWebsitePublished(true);
        } else {
            restaurant.setWebsitePublished(false);
        }
        if (!PlanLimits.canUseProServiceModules(plan, paymentStatus)) {
            restaurant.setHasPickup(false);
            restaurant.setHasDelivery(false);
            restaurant.setHasReservations(false);
        }
        applyPeriodEndIfPresent(restaurant, request.getCurrentPeriodEnd());

        Restaurant saved = restaurantRepository.save(restaurant);
        log.warn(
                "Suscripción SuperAdmin: actor={} restaurantId={} subdomain={} plan={}->{} payment={}->{} websitePublished={}",
                actor,
                saved.getId(),
                saved.getSubdomain(),
                previousPlan,
                saved.getPlan(),
                previousPayment,
                saved.getPaymentStatus(),
                saved.isWebsitePublished()
        );
        return toTenantResponse(saved);
    }

    @Transactional(readOnly = true)
    public ImpersonateResponse impersonate(Long restaurantId, String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) {
            throw new IllegalArgumentException("Se requiere el SuperAdmin autenticado para impersonar.");
        }
        String actor = actorEmail.trim().toLowerCase(Locale.ROOT);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurante no encontrado."));

        if (!restaurant.isActive()) {
            throw new IllegalArgumentException(
                    "No se puede ingresar a un restaurante suspendido. Actívalo primero."
            );
        }

        User owner = userRepository
                .findFirstByRestaurantIdAndRoleAndIsActiveTrueOrderByIdAsc(
                        restaurantId, UserRole.OWNER
                )
                .or(() -> userRepository.findFirstByRestaurantIdAndRoleAndIsActiveTrueOrderByIdAsc(
                        restaurantId, UserRole.ADMIN
                ))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay un OWNER/ADMIN activo para impersonar en este restaurante."
                ));

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(owner.getEmail())
                .password(owner.getPasswordHash())
                .authorities("ROLE_" + owner.getRole().name())
                .build();

        String token = jwtService.generateImpersonationToken(
                userDetails,
                restaurant.getId(),
                owner.getRole().name(),
                actor
        );
        String code = impersonationHandoffService.issue(
                token,
                restaurant.getSubdomain(),
                restaurant.getId()
        );
        long expiresInSeconds = Math.max(1L, jwtService.getImpersonationExpirationMs() / 1000L);
        long handoffExpiresInSeconds = impersonationHandoffService.ttlSeconds();

        // Auditoría: quién entró a qué tenant como quién (buscar en logs por "Impersonación").
        log.warn(
                "Impersonación: actor={} restaurantId={} subdomain={} asUser={} role={} expiresInSeconds={} handoffTtlSeconds={}",
                actor,
                restaurant.getId(),
                restaurant.getSubdomain(),
                owner.getEmail(),
                owner.getRole().name(),
                expiresInSeconds,
                handoffExpiresInSeconds
        );

        return ImpersonateResponse.builder()
                .code(code)
                .tenantSlug(restaurant.getSubdomain())
                .restaurantName(restaurant.getName())
                .loginPath("/admin/dashboard")
                .handoffExpiresInSeconds(handoffExpiresInSeconds)
                .expiresInSeconds(expiresInSeconds)
                .impersonatedBy(actor)
                .impersonatedAs(owner.getEmail())
                .build();
    }

    @Transactional(readOnly = true)
    public SuperAdminMetricsResponse metrics() {
        List<Restaurant> all = restaurantRepository.findAll();
        long total = all.size();
        long active = all.stream().filter(Restaurant::isActive).count();
        long suspended = total - active;
        long pro = all.stream()
                .filter(r -> r.getPlan() == SubscriptionPlan.PRO)
                .count();
        long basic = total - pro;
        EstimatedMrrCalculator.EstimatedMrr mrr = estimatedMrrCalculator.estimate(all);
        double churn = total == 0 ? 0.0 : (suspended * 100.0) / total;

        return SuperAdminMetricsResponse.builder()
                .totalTenants(total)
                .activeTenants(active)
                .suspendedTenants(suspended)
                .proTenants(pro)
                .basicTenants(basic)
                .estimatedMrr(mrr.amount())
                .estimatedMrrCurrency(mrr.currency())
                .estimatedMrrAsOf(mrr.asOf())
                .estimatedMrrPeriod(mrr.period())
                .estimatedMrrMethod(mrr.method())
                .estimatedMrrLabelEs(mrr.labelEs())
                .estimatedMrrDisclaimerEs(mrr.disclaimerEs())
                .estimatedMrrUnitPriceMxn(mrr.unitPriceMxn())
                .estimatedMrrProActiveCount(mrr.proActiveCount())
                .churnRate(Math.round(churn * 10.0) / 10.0)
                .registrationGrowth(buildRegistrationGrowth(all))
                .build();
    }

    private List<SuperAdminMetricsResponse.RegistrationPoint> buildRegistrationGrowth(
            List<Restaurant> all
    ) {
        YearMonth now = YearMonth.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, Long> byMonth = all.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        r -> YearMonth.from(r.getCreatedAt()).format(fmt),
                        Collectors.counting()
                ));

        List<SuperAdminMetricsResponse.RegistrationPoint> points = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            String key = ym.format(fmt);
            points.add(SuperAdminMetricsResponse.RegistrationPoint.builder()
                    .month(key)
                    .count(byMonth.getOrDefault(key, 0L))
                    .build());
        }
        return points;
    }

    private SuperAdminTenantResponse toTenantResponse(Restaurant r) {
        return SuperAdminTenantResponse.builder()
                .id(r.getId())
                .name(r.getName())
                .subdomain(r.getSubdomain())
                .plan(r.getPlan() != null ? r.getPlan().name() : "BASIC")
                .paymentStatus(r.getPaymentStatus() != null
                        ? r.getPaymentStatus().name()
                        : "ACTIVE")
                .currentPeriodStart(format(r.getCurrentPeriodStart()))
                .currentPeriodEnd(format(r.getCurrentPeriodEnd()))
                .billingInterval(r.getBillingInterval() != null
                        ? r.getBillingInterval().name()
                        : null)
                .active(r.isActive())
                .websitePublished(r.isWebsitePublished())
                .createdAt(format(r.getCreatedAt()))
                .updatedAt(format(r.getUpdatedAt()))
                .build();
    }

    /**
     * Null = no cambiar. Cadena vacía = limpiar período.
     */
    private static void applyPeriodEndIfPresent(Restaurant restaurant, String rawPeriodEnd) {
        if (rawPeriodEnd == null) {
            return;
        }
        String trimmed = rawPeriodEnd.trim();
        if (trimmed.isEmpty()) {
            SubscriptionPeriodSupport.setPeriodEnd(restaurant, null);
            return;
        }
        try {
            SubscriptionPeriodSupport.setPeriodEnd(restaurant, OffsetDateTime.parse(trimmed));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "La fecha de renovación debe ser ISO-8601 (ej. 2026-12-31T23:59:59Z)."
            );
        }
    }

    private static String format(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
