package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.TableCallRequest;
import com.platolisto.restaurant_backend.dto.TableCallResponse;
import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import com.platolisto.restaurant_backend.entity.TableCallType;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableCallServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private TableQrTokenService tableQrTokenService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private TableCallService tableCallService;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder()
                .id(7L)
                .name("Demo")
                .subdomain("demo")
                .isActive(true)
                .plan(SubscriptionPlan.BASIC)
                .paymentStatus(PaymentStatus.ACTIVE)
                .build();
        TenantContext.setCurrentTenant(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createCall_publishesTableCallEvent() {
        when(restaurantRepository.findById(7L)).thenReturn(Optional.of(restaurant));
        doNothing().when(tableQrTokenService).requireValid(eq(restaurant), eq("12"), eq("tok"));

        TableCallRequest request = TableCallRequest.builder()
                .tableNumber("12")
                .tableToken("tok")
                .callType(TableCallType.WAITER)
                .build();

        TableCallResponse response = tableCallService.createCall(request);

        assertThat(response.getEventType()).isEqualTo(TableCallResponse.EVENT_TYPE);
        assertThat(response.getCallType()).isEqualTo(TableCallType.WAITER);
        assertThat(response.getTableNumber()).isEqualTo("12");
        assertThat(response.getId()).isNotNull();

        ArgumentCaptor<TableCallResponse> captor = ArgumentCaptor.forClass(TableCallResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/admin/demo/table-calls"), captor.capture());
        assertThat(captor.getValue().getCallType()).isEqualTo(TableCallType.WAITER);
    }

    @Test
    void createCall_billWithInvalidPayment_rejects() {
        when(restaurantRepository.findById(7L)).thenReturn(Optional.of(restaurant));
        doNothing().when(tableQrTokenService).requireValid(any(), any(), any());

        TableCallRequest request = TableCallRequest.builder()
                .tableNumber("3")
                .tableToken("tok")
                .callType(TableCallType.BILL)
                .paymentMethod("BITCOIN")
                .build();

        assertThatThrownBy(() -> tableCallService.createCall(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Forma de pago");
    }
}
