package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.CategoryResponse;
import com.platolisto.restaurant_backend.dto.MenuImportResultDTO;
import com.platolisto.restaurant_backend.dto.MenuImportRowError;
import com.platolisto.restaurant_backend.dto.ProductResponse;
import com.platolisto.restaurant_backend.entity.Category;
import com.platolisto.restaurant_backend.entity.Product;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.plan.PlanLimits;
import com.platolisto.restaurant_backend.repository.CategoryRepository;
import com.platolisto.restaurant_backend.repository.ProductRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.util.CategoryNameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuImportService {

    private static final String[] HEADERS = {
            "Categoria",
            "Nombre_Platillo",
            "Descripcion",
            "Precio",
            "Disponible",
            "Url_Imagen"
    };

    private static final int MAX_CATEGORY_LEN = 50;
    private static final int MAX_NAME_LEN = 100;
    private static final int MAX_IMAGE_URL_LEN = 512;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final RestaurantRepository restaurantRepository;

    /**
     * Genera la plantilla .xlsx estilizada con una fila de ejemplo.
     */
    public InputStreamResource generateExcelTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Menu");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle exampleStyle = workbook.createCellStyle();
            Font exampleFont = workbook.createFont();
            exampleFont.setItalic(true);
            exampleFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            exampleStyle.setFont(exampleFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            Row example = sheet.createRow(1);
            String[] sample = {
                    "Entradas",
                    "Nachos supremas",
                    "Totopos con queso, guacamole y pico de gallo",
                    "89.00",
                    "TRUE",
                    "https://ejemplo.com/nachos.jpg"
            };
            for (int i = 0; i < sample.length; i++) {
                Cell cell = example.createCell(i);
                cell.setCellValue(sample[i]);
                cell.setCellStyle(exampleStyle);
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 512, 12000));
            }

            workbook.write(out);
            return new InputStreamResource(new ByteArrayInputStream(out.toByteArray()));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la plantilla Excel.", e);
        }
    }

    /**
     * Importa platillos desde .xlsx (o .csv compatible con las mismas columnas).
     * El tenant se toma de {@link TenantContext} (header X-Tenant), no de un UUID.
     */
    @Transactional
    public MenuImportResultDTO importMenuFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debes seleccionar un archivo Excel (.xlsx) o CSV.");
        }

        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));

        String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase(Locale.ROOT)
                : "";
        List<ImportRow> rows;
        try {
            if (filename.endsWith(".csv")) {
                rows = readCsvRows(file.getInputStream());
            } else if (filename.endsWith(".xlsx") || filename.endsWith(".xlsm")
                    || isProbablyXlsx(file)) {
                rows = readExcelRows(file.getInputStream());
            } else {
                throw new IllegalArgumentException(
                        "Formato no soportado. Usa la plantilla .xlsx o un CSV con las mismas columnas."
                );
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error leyendo archivo de menú: {}", e.getMessage());
            throw new IllegalArgumentException(
                    "No se pudo leer el archivo. Verifica que sea una plantilla válida."
            );
        }

        Map<String, Category> categoriesByKey = loadCategoryCache();
        int nextDisplayOrder = categoriesByKey.values().stream()
                .mapToInt(Category::getDisplayOrder)
                .max()
                .orElse(-1) + 1;

        long activeCount = productRepository.countByRestaurant_Id(restaurantId);
        SubscriptionPlan plan = restaurant.getPlan() != null
                ? restaurant.getPlan()
                : SubscriptionPlan.BASIC;

        List<MenuImportRowError> errors = new ArrayList<>();
        List<ProductResponse> createdProducts = new ArrayList<>();
        List<CategoryResponse> createdCategories = new ArrayList<>();
        int processed = 0;

        for (ImportRow row : rows) {
            processed++;
            try {
                ValidatedDish dish = validateRow(row);
                if (!PlanLimits.canCreateProduct(plan, activeCount + createdProducts.size())) {
                    errors.add(MenuImportRowError.builder()
                            .row(row.rowNumber())
                            .reason("Límite del Plan Básico alcanzado ("
                                    + PlanLimits.BASIC_MAX_PRODUCTS
                                    + " platillos). Filas restantes omitidas.")
                            .build());
                    break;
                }

                Category category = categoriesByKey.get(dish.categoryKey());
                if (category == null) {
                    category = Category.builder()
                            .restaurant(restaurant)
                            .name(dish.categoryDisplayName())
                            .displayOrder(nextDisplayOrder++)
                            .build();
                    category = categoryRepository.save(category);
                    categoriesByKey.put(dish.categoryKey(), category);
                    createdCategories.add(mapCategory(category));
                }

                Product product = Product.builder()
                        .restaurant(restaurant)
                        .category(category)
                        .name(dish.name())
                        .description(dish.description())
                        .price(dish.price())
                        .imageUrl(dish.imageUrl())
                        .isAvailable(dish.available())
                        .build();

                Product saved = productRepository.save(product);
                createdProducts.add(mapProduct(saved));
            } catch (RowValidationException e) {
                errors.add(MenuImportRowError.builder()
                        .row(row.rowNumber())
                        .reason(e.getMessage())
                        .build());
            }
        }

        log.info(
                "Importación menú tenant={}: procesados={}, creados={}, errores={}",
                restaurant.getSubdomain(),
                processed,
                createdProducts.size(),
                errors.size()
        );

        return MenuImportResultDTO.builder()
                .totalProcesados(processed)
                .creadosExitosamente(createdProducts.size())
                .errores(errors)
                .products(createdProducts)
                .categoriesCreated(createdCategories)
                .build();
    }

    private Map<String, Category> loadCategoryCache() {
        Map<String, Category> map = new HashMap<>();
        List<Category> existing = categoryRepository.findAll(
                Sort.by(Sort.Order.asc("displayOrder"))
        );
        for (Category category : existing) {
            String key = CategoryNameNormalizer.toMatchKey(category.getName());
            map.putIfAbsent(key, category);
        }
        return map;
    }

    private ValidatedDish validateRow(ImportRow row) {
        String categoryRaw = row.categoria();
        String nameRaw = row.nombre();
        String descriptionRaw = row.descripcion();
        String priceRaw = row.precio();
        String availableRaw = row.disponible();
        String imageUrlRaw = row.imageUrl();

        if (isBlank(categoryRaw)) {
            throw new RowValidationException("La categoría es obligatoria.");
        }
        if (isBlank(nameRaw)) {
            throw new RowValidationException("El nombre del platillo es obligatorio.");
        }

        String categoryDisplay = CategoryNameNormalizer.toDisplayName(categoryRaw);
        if (categoryDisplay.length() > MAX_CATEGORY_LEN) {
            throw new RowValidationException(
                    "La categoría supera " + MAX_CATEGORY_LEN + " caracteres."
            );
        }

        String name = nameRaw.trim().replaceAll("\\s+", " ");
        if (name.length() > MAX_NAME_LEN) {
            throw new RowValidationException(
                    "El nombre supera " + MAX_NAME_LEN + " caracteres."
            );
        }

        BigDecimal price = parsePrice(priceRaw);
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RowValidationException("El precio debe ser un número mayor a 0.");
        }

        Boolean available = parseAvailable(availableRaw);
        if (available == null) {
            throw new RowValidationException(
                    "Disponible debe ser TRUE o FALSE (también se acepta Sí/No)."
            );
        }

        String description = isBlank(descriptionRaw)
                ? null
                : descriptionRaw.trim();

        String imageUrl = parseOptionalImageUrl(imageUrlRaw);

        return new ValidatedDish(
                CategoryNameNormalizer.toMatchKey(categoryDisplay),
                categoryDisplay,
                name,
                description,
                price.setScale(2, RoundingMode.HALF_UP),
                available,
                imageUrl
        );
    }

    private static String parseOptionalImageUrl(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        String url = raw.trim();
        if (url.length() > MAX_IMAGE_URL_LEN) {
            throw new RowValidationException(
                    "Url_Imagen supera " + MAX_IMAGE_URL_LEN + " caracteres."
            );
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new RowValidationException(
                    "Url_Imagen debe iniciar con http:// o https://."
            );
        }
        return url;
    }

    private static BigDecimal parsePrice(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        String normalized = raw.trim()
                .replace("$", "")
                .replace(" ", "")
                .replace(",", ".");
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean parseAvailable(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "true", "1", "si", "sí", "yes", "y", "s" -> true;
            case "false", "0", "no", "n" -> false;
            default -> null;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isProbablyXlsx(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.contains("spreadsheet");
    }

    private List<ImportRow> readExcelRows(InputStream inputStream) throws IOException {
        List<ImportRow> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("El archivo no contiene hojas.");
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            int first = sheet.getFirstRowNum();
            int last = sheet.getLastRowNum();
            // Fila 0 = encabezados
            for (int i = first + 1; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isExcelRowBlank(row, formatter)) {
                    continue;
                }
                rows.add(new ImportRow(
                        i + 1,
                        cellString(row, 0, formatter),
                        cellString(row, 1, formatter),
                        cellString(row, 2, formatter),
                        cellString(row, 3, formatter),
                        cellString(row, 4, formatter),
                        cellString(row, 5, formatter)
                ));
            }
        }
        return rows;
    }

    private static boolean isExcelRowBlank(Row row, DataFormatter formatter) {
        for (int c = 0; c < HEADERS.length; c++) {
            if (!isBlank(cellString(row, c, formatter))) {
                return false;
            }
        }
        return true;
    }

    private static String cellString(Row row, int index, DataFormatter formatter) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    private List<ImportRow> readCsvRows(InputStream inputStream) throws IOException {
        List<ImportRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rows;
            }
            // Detectar BOM
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            char separator = headerLine.contains(";") && !headerLine.contains(",") ? ';' : ',';
            int rowNumber = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = splitCsvLine(line, separator);
                rows.add(new ImportRow(
                        rowNumber,
                        col(cols, 0),
                        col(cols, 1),
                        col(cols, 2),
                        col(cols, 3),
                        col(cols, 4),
                        col(cols, 5)
                ));
            }
        }
        return rows;
    }

    private static String col(String[] cols, int index) {
        if (index >= cols.length) {
            return "";
        }
        return cols[index].trim();
    }

    /** Split CSV simple con soporte de comillas dobles. */
    private static String[] splitCsvLine(String line, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == separator && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts.toArray(String[]::new);
    }

    private ProductResponse mapProduct(Product product) {
        return ProductResponse.builder()
                .uuid(product.getUuid())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .imageUrl(product.getImageUrl())
                .isAvailable(product.isAvailable())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .createdAt(product.getCreatedAt())
                .build();
    }

    private CategoryResponse mapCategory(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .displayOrder(category.getDisplayOrder())
                .createdAt(category.getCreatedAt())
                .build();
    }

    private record ImportRow(
            int rowNumber,
            String categoria,
            String nombre,
            String descripcion,
            String precio,
            String disponible,
            String imageUrl
    ) {
    }

    private record ValidatedDish(
            String categoryKey,
            String categoryDisplayName,
            String name,
            String description,
            BigDecimal price,
            boolean available,
            String imageUrl
    ) {
    }

    private static final class RowValidationException extends RuntimeException {
        private RowValidationException(String message) {
            super(message);
        }
    }
}
