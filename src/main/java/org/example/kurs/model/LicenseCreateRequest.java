package org.example.kurs.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LicenseCreateRequest {

    private Long productId;        // Идентификатор продукта
    private Long ownerId;          // Идентификатор владельца
    private Long licenseTypeId;    // Идентификатор типа лицензии
    private String description;    // Описание лицензии
    private Integer deviceCount;   // Количество устройств, на которые можно установить лицензию

}
