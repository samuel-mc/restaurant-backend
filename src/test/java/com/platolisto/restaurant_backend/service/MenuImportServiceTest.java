package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.plan.PlanLimits;
import com.platolisto.restaurant_backend.repository.CategoryRepository;
import com.platolisto.restaurant_backend.repository.ProductRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuImportServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private MenuImportService menuImportService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rejectsImportThatWouldExceedBasicLimit() {
        Restaurant restaurant = Restaurant.builder()
                .id(7L)
                .name("Demo")
                .subdomain("demo")
                .plan(SubscriptionPlan.BASIC)
                .paymentStatus(PaymentStatus.ACTIVE)
                .build();
        when(restaurantRepository.findById(7L)).thenReturn(Optional.of(restaurant));
        when(productRepository.countByRestaurant_Id(7L)).thenReturn(18L);

        MockMultipartFile file = csvFile("""
                Categoria,Nombre_Platillo,Descripcion,Precio,Disponible,URL_Imagen
                Entradas,Platillo A,,10,TRUE,
                Entradas,Platillo B,,10,TRUE,
                Entradas,Platillo C,,10,TRUE,
                """);

        assertThatThrownBy(() -> menuImportService.importMenuFromExcel(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PlanLimits.basicImportWouldExceedMessage(18, 3));
    }

    @Test
    void rejectsImportWhenProPendingPaymentAtCapacity() {
        Restaurant restaurant = Restaurant.builder()
                .id(7L)
                .name("Demo")
                .subdomain("demo")
                .plan(SubscriptionPlan.PRO)
                .paymentStatus(PaymentStatus.PENDING_PAYMENT)
                .build();
        when(restaurantRepository.findById(7L)).thenReturn(Optional.of(restaurant));
        when(productRepository.countByRestaurant_Id(7L))
                .thenReturn((long) PlanLimits.BASIC_MAX_PRODUCTS);

        MockMultipartFile file = csvFile("""
                Categoria,Nombre_Platillo,Descripcion,Precio,Disponible,URL_Imagen
                Entradas,Platillo Extra,,10,TRUE,
                """);

        assertThatThrownBy(() -> menuImportService.importMenuFromExcel(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PlanLimits.BASIC_PRODUCT_LIMIT_UPGRADE_MESSAGE);
    }

    private static MockMultipartFile csvFile(String content) {
        return new MockMultipartFile(
                "file",
                "menu.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }
}
