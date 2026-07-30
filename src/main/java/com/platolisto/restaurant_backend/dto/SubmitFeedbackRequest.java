package com.platolisto.restaurant_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitFeedbackRequest {

    @NotNull(message = "Indica de 1 a 5 estrellas.")
    @Min(value = 1, message = "La calificación mínima es 1.")
    @Max(value = 5, message = "La calificación máxima es 5.")
    private Integer stars;

    /** Requerido para 1–3 estrellas (validado en servicio). */
    @Size(max = 1000, message = "El comentario no puede superar 1000 caracteres.")
    private String comment;

    @Size(max = 120, message = "El contacto no puede superar 120 caracteres.")
    private String contact;

    /** Motivo opcional: FOOD | SERVICE | WAIT | OTHER */
    @Size(max = 40, message = "Motivo inválido.")
    private String reason;
}
