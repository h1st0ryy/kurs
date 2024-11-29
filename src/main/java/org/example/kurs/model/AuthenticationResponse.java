package org.example.kurs.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO для ответа с результатом аутентификации.
 */
@Data
@AllArgsConstructor
public class AuthenticationResponse {

    private String email;
    private String token;
}
