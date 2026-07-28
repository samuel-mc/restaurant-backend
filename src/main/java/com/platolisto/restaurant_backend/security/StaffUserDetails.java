package com.platolisto.restaurant_backend.security;

import com.platolisto.restaurant_backend.entity.StaffMember;
import com.platolisto.restaurant_backend.entity.StaffRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Principal de autenticación para miembros del equipo (login por PIN).
 * El username es {@code staff:{uuid}} para no colisionar con emails de {@code User}.
 */
@Getter
public class StaffUserDetails implements UserDetails {

    public static final String SUBJECT_PREFIX = "staff:";

    private final UUID staffId;
    private final Long restaurantId;
    private final String name;
    private final StaffRole role;
    private final boolean active;

    public StaffUserDetails(
            UUID staffId,
            Long restaurantId,
            String name,
            StaffRole role,
            boolean active
    ) {
        this.staffId = staffId;
        this.restaurantId = restaurantId;
        this.name = name;
        this.role = role;
        this.active = active;
    }

    /**
     * Uso en flujos con sesión Hibernate abierta (login).
     * Prefiere {@link #fromMember(StaffMember, Long)} en filtros sin OSIV.
     */
    public static StaffUserDetails fromMember(StaffMember staff, Long restaurantId) {
        return new StaffUserDetails(
                staff.getId(),
                restaurantId,
                staff.getName(),
                staff.getRole(),
                staff.isActive()
        );
    }

    public static String subjectFor(UUID staffId) {
        return SUBJECT_PREFIX + staffId;
    }

    public static UUID parseStaffId(String subject) {
        if (subject == null || !subject.startsWith(SUBJECT_PREFIX)) {
            return null;
        }
        return UUID.fromString(subject.substring(SUBJECT_PREFIX.length()));
    }

    public static boolean isStaffSubject(String subject) {
        return subject != null && subject.startsWith(SUBJECT_PREFIX);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return subjectFor(staffId);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
