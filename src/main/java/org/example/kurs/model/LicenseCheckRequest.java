package org.example.kurs.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LicenseCheckRequest {

    private String macAddress;   // MAC-адрес устройства для поиска
    private String deviceName;   // Имя устройства для поиска
}
