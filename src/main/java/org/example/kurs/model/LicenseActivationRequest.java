package org.example.kurs.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LicenseActivationRequest {

    private String code;  // Код лицензии, по которому мы будем искать лицензию для активации
    private String macAddress;   // MAC-адрес устройства, на которое активируется лицензия
    private String deviceName;   // Имя устройства, на которое активируется лицензия

}
