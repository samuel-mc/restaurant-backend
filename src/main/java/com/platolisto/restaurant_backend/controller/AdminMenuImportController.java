package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.MenuImportResultDTO;
import com.platolisto.restaurant_backend.service.MenuImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/menu")
@RequiredArgsConstructor
public class AdminMenuImportController {

    private static final String TEMPLATE_FILENAME = "plantilla_menu_platolisto.xlsx";

    private final MenuImportService menuImportService;

    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() {
        InputStreamResource resource = menuImportService.generateExcelTemplate();
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + TEMPLATE_FILENAME + "\""
                )
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .body(resource);
    }

    @PostMapping(value = "/upload-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MenuImportResultDTO> uploadExcel(
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(menuImportService.importMenuFromExcel(file));
    }
}
