package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.StaffMemberRequest;
import com.platolisto.restaurant_backend.dto.StaffMemberResponse;
import com.platolisto.restaurant_backend.dto.StaffMemberUpdateRequest;
import com.platolisto.restaurant_backend.service.StaffTeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/team")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class AdminTeamController {

    private final StaffTeamService staffTeamService;

    @GetMapping
    public ResponseEntity<List<StaffMemberResponse>> listTeam() {
        return ResponseEntity.ok(staffTeamService.listTeam());
    }

    @PostMapping
    public ResponseEntity<StaffMemberResponse> createMember(
            @Valid @RequestBody StaffMemberRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffTeamService.createMember(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<StaffMemberResponse> updateMember(
            @PathVariable UUID id,
            @Valid @RequestBody StaffMemberUpdateRequest request
    ) {
        return ResponseEntity.ok(staffTeamService.updateMember(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateMember(@PathVariable UUID id) {
        staffTeamService.deactivateMember(id);
        return ResponseEntity.noContent().build();
    }
}
