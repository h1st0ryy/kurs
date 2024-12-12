package org.example.kurs.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.User;
import org.example.kurs.model.*;
import org.example.kurs.repository.*;
import org.example.kurs.service.LicenseHistoryService;
import org.example.kurs.configuration.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO: 1. Не сохранять тикеты
//TODO: 2. Добавить CRUD для Device, Product, LicenseType

@RestController
@RequestMapping("/licensing")
@RequiredArgsConstructor
public class LicensingController {

    private final JwtTokenProvider jwtTokenProvider;
    private final ProductRepository productRepository;
    private final ApplicationUserRepository applicationUserRepository;
    private final LicenseTypeRepository licenseTypeRepository;
    private final LicenseRepository licenseRepository;
    private final DeviceLicenseRepository deviceLicenseRepository;
    private final LicenseHistoryService licenseHistoryService;
    private final DeviceRepository deviceRepository;

    // Метод для преобразования LocalDate в Date
    private Date convertLocalDateToDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }


    @PostMapping("/create")
    public ResponseEntity<?> createLicense(HttpServletRequest request, @RequestBody LicenseCreateRequest requestData) {
        Logger logger = LoggerFactory.getLogger(getClass());

        try {
            // 1. Извлекаем роли из токена, используя resolveToken и getRolesFromToken
            logger.info("Извлечение роли из токена...");
            Set<String> roles = jwtTokenProvider.getRolesFromRequest(request);  // Используем новый метод
            logger.info("Роль извлечена из токена: {}", roles);

            // 2. Проверяем, что роль пользователя - ADMIN
            if (!roles.contains("ROLE_ADMIN")) {
                // Логируем предупреждение о попытке создания лицензии без прав
                logger.warn("Попытка создать лицензию без прав ADMIN. Роль: {}", roles);

                // Возвращаем ответ с кодом FORBIDDEN и сообщением
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Нет прав для создания лицензии");
            }

            // 3. Проверка существования продукта по ID
            logger.info("Проверка существования продукта с ID: {}", requestData.getProductId());
            Product product = productRepository.findById(requestData.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Продукт не найден"));
            logger.info("Продукт найден: {}", product.getName());

            // 3.1 Проверка, заблокирован ли продукт
            if (product.isBlocked()) {  // Используем поле isBlocked для проверки
                logger.warn("Продукт с ID: {} заблокирован", requestData.getProductId());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Продукт заблокирован, лицензия не может быть создана");
            }

            // 4. Проверка существования пользователя по ID (владельца)
            logger.info("Проверка существования пользователя с ID: {}", requestData.getOwnerId());
            ApplicationUser owner = applicationUserRepository.findById(requestData.getOwnerId())
                    .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
            logger.info("Пользователь найден: {}", owner.getUsername());

            // 5. Проверка существования типа лицензии по ID
            logger.info("Проверка существования типа лицензии с ID: {}", requestData.getLicenseTypeId());
            LicenseType licenseType = licenseTypeRepository.findById(requestData.getLicenseTypeId())
                    .orElseThrow(() -> new IllegalArgumentException("Тип лицензии не найден"));
            logger.info("Тип лицензии найден: {}", licenseType.getName());

            // 6. Создание новой лицензии
            logger.info("Создание новой лицензии...");
            License newLicense = new License();
            newLicense.setCode(generateActivationCode());  // Генерация активационного кода
            logger.info("Активационный код сгенерирован: {}", newLicense.getCode());

            //TODO: 1. Пересмотреть логику установки UserId

            // Устанавливаем пользователя (user_id)
            newLicense.setOwner(owner);  // Устанавливаем владельца лицензии (owner_id)
            newLicense.setProduct(product);  // Устанавливаем продукт (product_id)
            newLicense.setLicenseType(licenseType);  // Устанавливаем тип лицензии (type_id)

            // Преобразуем LocalDate в Date для firstActivationDate
            newLicense.setFirstActivationDate(convertLocalDateToDate(LocalDate.now()));

            // 7. Проверка параметров и расчет даты окончания
            int duration = licenseType.getDefaultDuration(); // Срок действия по умолчанию для типа лицензии
            LocalDate endingLocalDate = LocalDate.now().plusDays(duration);
            newLicense.setEndingDate(convertLocalDateToDate(endingLocalDate));

            // Устанавливаем статус блокировки
            newLicense.setBlocked(false);  // По умолчанию лицензия не заблокирована

            // Устанавливаем количество устройств
            newLicense.setDeviceCount(requestData.getDeviceCount());
            newLicense.setDuration(duration);
            newLicense.setDescription(requestData.getDescription() != null ? requestData.getDescription() : "Приятного пользования нашими продуктами!");

            // Сохраняем лицензию в базе данных
            logger.info("Сохранение лицензии в базе данных...");
            licenseRepository.save(newLicense);
            logger.info("Лицензия успешно сохранена в базе данных с ID: {}", newLicense.getId());

            // 8. Запись в history
            String description = "Лицензия создана";
            Date changeDate = convertLocalDateToDate(LocalDate.now());
            licenseHistoryService.recordLicenseChange(newLicense.getId(), owner.getId(), "Создана", changeDate, description);
            logger.info("Запись изменений лицензии в историю завершена");

            return ResponseEntity.status(HttpStatus.CREATED).body("Лицензия успешно создана");

        } catch (IllegalArgumentException e) {
            logger.error("Ошибка при создании лицензии: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Произошла ошибка при создании лицензии: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Произошла ошибка при создании лицензии");
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateLicense(HttpServletRequest request, @RequestBody LicenseUpdateRequest requestData) {
        Logger logger = LoggerFactory.getLogger(getClass());

        try {
            // 1. Извлекаем роли из токена
            Set<String> roles = jwtTokenProvider.getRolesFromRequest(request);
            logger.info("Роль извлечена из токена: {}", roles);

            // 2. Проверка аутентификации пользователя
            if (roles.isEmpty()) {
                logger.error("Ошибка аутентификации: отсутствуют роли");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Ошибка аутентификации");
            }

            // 3. Проверка действительности ключа лицензии
            License license = licenseRepository.findByCode(requestData.getCode())
                    .orElseThrow(() -> new IllegalArgumentException("Недействительный ключ лицензии"));
            logger.info("Лицензия с кодом {} найдена", requestData.getCode());

            if (equals(license.getUser().getId())) {
                logger.error("Ошибка: пользователь не является владельцем лицензии");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ошибка: пользователь не является владельцем лицензии");
            }

            // 4. Проверка возможности продления
            if (license.getBlocked()) {
                logger.warn("Лицензия с кодом {} заблокирована", requestData.getCode());
                // Создаем тикет с отказом в продлении
                Ticket ticket = Ticket.createTicket(license.getOwner().getId(),
                        true,  // Заблокированная лицензия
                        null); // Без даты окончания, так как отказ в продлении

                // Логируем тикет
                logger.info("Тикет: {}", ticket);

                // Отправляем ответ с тикетом
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Лицензия заблокирована, продление невозможно.");
            }

            if (license.getEndingDate().before(new Date())) {
                logger.warn("Лицензия с кодом {} уже истекла", requestData.getCode());
                // Создаем тикет с отказом в продлении
                Ticket ticket = Ticket.createTicket(license.getOwner().getId(),
                        false,  // Лицензия не заблокирована, но уже истекла
                        null);  // Без даты окончания, так как продление невозможно

                // Логируем тикет
                logger.info("Тикет: {}", ticket);


                // Отправляем ответ с тикетом
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Лицензия истекла, продление невозможно.");
            }

            // 5. Преобразование newExpirationDate из String в Date
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            Date newExpirationDate = sdf.parse(requestData.getNewExpirationDate()); // Парсим строку в Date

            logger.info("Новая дата окончания: {}", newExpirationDate);

            // Проверка, что новая дата окончания больше текущей даты окончания лицензии
            if (newExpirationDate.compareTo(license.getEndingDate()) <= 0) {
                logger.warn("Новая дата окончания лицензии {} не может быть меньше или равна текущей дате окончания {}",
                        requestData.getNewExpirationDate(), license.getEndingDate());

                // Создаем тикет с отказом в продлении
                Ticket ticket = Ticket.createTicket(
                        license.getOwner().getId(), // ID владельца лицензии
                        false,  // Лицензия не заблокирована, но ошибка в дате окончания
                        null   // Без даты окончания, так как произошла ошибка
                );

                // Логируем тикет
                logger.info("Тикет: {}", ticket);


                // Отправляем ответ с тикетом
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Новая дата окончания лицензии не может быть меньше или равна текущей. Тикет с отказом был создан.");
            }

            int newDuration = calculateDaysBetween(newExpirationDate);
            // 6. Продление лицензии
            license.setEndingDate(newExpirationDate); // Устанавливаем новую дату окончания, переданную в запросе
            license.setDuration(newDuration);

            // Сохраняем изменения в базе данных
            licenseRepository.save(license);
            logger.info("Лицензия с кодом {} продлена до: {}", requestData.getCode(), newExpirationDate);

            // 7. Создание тикета с подтверждением продления
            Ticket ticket = Ticket.createTicket(license.getOwner().getId(),
                    false,  // Лицензия не заблокирована
                    newExpirationDate);  // Устанавливаем новую дату окончания

            // Проверка записи в таблице device_license
            Optional<DeviceLicense> deviceLicenseOpt = deviceLicenseRepository.findByLicenseId(license.getId());
            Date activationDate = null;
            Long deviceId = deviceLicenseOpt.get().getDeviceId();
            String deviceMessage = "Лицензия не активирована на устройстве";

            if (deviceLicenseOpt.isPresent()) {
                DeviceLicense deviceLicense = deviceLicenseOpt.get();

                activationDate = deviceLicense.getActivationDate();  // Дата активации
                deviceId = deviceLicense.getDeviceId();  // Идентификатор устройства

                // Сообщение для подтверждения активации
                deviceMessage = "Лицензия активирована на устройстве";
            }

            // Устанавливаем данные для тикета
            ticket.setActivationDate(activationDate);
            ticket.setDeviceId(deviceId);
            ticket.setTicketLifetime(newDuration);
            // Логируем тикет
            logger.info("Тикет: {}", ticket);

            // Отправляем ответ с текстом сообщения (разделяем на две строки)
            return ResponseEntity.status(HttpStatus.OK).body(deviceMessage + "\nЛицензия продлена до: " + newExpirationDate);

        } catch (ParseException e) {
            logger.error("Ошибка при парсинге даты: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Неверный формат даты.");
        } catch (IllegalArgumentException e) {
            logger.error("Ошибка: {}", e.getMessage());
            // Создаем тикет с ошибкой для недействительного ключа
            Ticket ticket = Ticket.createTicket(null,
                    false,  // Без блокировки, ошибка при продлении
                    null);  // Без даты окончания, так как произошла ошибка

            // Логируем тикет
            logger.info("Тикет: {}", ticket);

            // Отправляем ответ с тикетом
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Недействительный ключ лицензии.");
        } catch (Exception e) {
            logger.error("Произошла ошибка: {}", e.getMessage());
            // Создаем тикет с ошибкой для неизвестной ошибки
            Ticket ticket = Ticket.createTicket(null,
                    false,  // Без блокировки, ошибка при продлении
                    null);  // Без даты окончания, так как произошла ошибка

            // Логируем тикет
            logger.info("Тикет: {}", ticket);

            // Отправляем ответ с тикетом
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Произошла ошибка при продлении лицензии.");
        }
    }

    // Метод для вычисления количества дней между текущей датой и переданной датой
    public static int calculateDaysBetween(Date expirationDate) {
        // Преобразуем Date в LocalDate для удобства работы с датами
        LocalDateTime currentDate = LocalDateTime.now(); // Текущая дата и время
        LocalDate expirationLocalDate = expirationDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate(); // Преобразуем Date в LocalDate

        // Рассчитываем разницу в днях
        long daysBetween = ChronoUnit.DAYS.between(currentDate.toLocalDate(), expirationLocalDate);

        return (int) daysBetween; // Возвращаем количество дней в виде целого числа
    }


    @PostMapping("/check")
    public ResponseEntity<?> checkLicense(HttpServletRequest request, @RequestBody LicenseCheckRequest requestData) {
        Logger logger = LoggerFactory.getLogger(getClass());

        try {
            // 1. Извлекаем роли из токена
            Set<String> roles = jwtTokenProvider.getRolesFromRequest(request);
            logger.info("Роль извлечена из токена: {}", roles);

            // 2. Проверка аутентификации пользователя
            if (roles.isEmpty()) {
                logger.error("Ошибка аутентификации: отсутствуют роли");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Ошибка аутентификации");
            }

            // 3. Поиск устройства по MAC-адресу и имени
            Optional<Device> deviceOptional = deviceRepository.findByMacAddressAndName(requestData.getMacAddress(), requestData.getDeviceName());
            if (!deviceOptional.isPresent()) {
                logger.error("Ошибка: устройство не найдено с MAC-адресом {} и именем {}", requestData.getMacAddress(), requestData.getDeviceName());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Устройство не найдено");
            }
            Device device = deviceOptional.get();
            logger.info("Устройство найдено: {}", device);

            // 4. Получение информации о лицензиях устройства
            Optional<DeviceLicense> deviceLicenseOptional = deviceLicenseRepository.findByDeviceId(device.getId());

            if (!deviceLicenseOptional.isPresent()) {
                logger.warn("Лицензия не найдена для устройства с ID {}", device.getId());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Активная лицензия для устройства не найдена");
            }

            // 5. Лицензия найдена, извлекаем license_id и находим соответствующую лицензию в таблице licenses
            DeviceLicense deviceLicense = deviceLicenseOptional.get();

            // Находим лицензию по license_id
            Optional<License> licenseOptional = licenseRepository.findById(deviceLicense.getLicenseId());

            if (licenseOptional.isPresent()) {
                License license = licenseOptional.get();
                // Используем ending_date из лицензии вместо expirationDate
                Ticket ticket = Ticket.createTicket(license.getUser().getId(), false, license.getEndingDate());
                ticket.setDeviceId(deviceLicense.getDeviceId());

                logger.info("Тикет с подтверждением лицензии: {}", ticket);

                // Отправляем ответ с тикетом
                return ResponseEntity.status(HttpStatus.OK).body("Лицензия активирована на устройстве. Тикет: " + ticket.getId());

            } else {
                // Логика, если лицензия не найдена (например, обработка ошибки)
                logger.error("Лицензия с ID {} не найдена", deviceLicense.getLicenseId());
                // Создаем тикет с ошибкой
                Ticket ticket = Ticket.createTicket(null, true, null);
                // Отправляем ответ с тикетом
                return ResponseEntity.status(HttpStatus.OK).body("Лицензия активирована на устройстве. Тикет: " + ticket);
            }

        } catch (Exception e) {
            logger.error("Произошла ошибка при проверке лицензии: {}", e.getMessage());
            Ticket ticket = Ticket.createTicket(null, false, null);  // Ошибка без данных лицензии
            logger.info("Тикет с ошибкой: {}", ticket);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Произошла ошибка при проверке лицензии.");
        }
    }


    @PostMapping("/activation")
    public ResponseEntity<?> activateLicense(HttpServletRequest request, @RequestBody LicenseActivationRequest activationRequest) {
        Logger logger = LoggerFactory.getLogger(getClass());
        //TODO: проверить существование устройства, проверка доступа к лицензии
        // по счетчику
        try {
            // 1. Извлекаем роли из токена
            Set<String> roles = jwtTokenProvider.getRolesFromRequest(request);
            logger.info("Роль извлечена из токена: {}", roles);

            // 2. Проверка аутентификации пользователя
            if (roles.isEmpty()) {
                logger.error("Ошибка аутентификации: отсутствуют роли");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Ошибка аутентификации");
            }
            // Поиск лицензии по коду
            Optional<License> licenseOptional = licenseRepository.findByCode(activationRequest.getCode());
            License license = licenseOptional.get();



            // 4. Проверка лицензии по коду;
            if (!licenseOptional.isPresent()) {
                logger.error("Лицензия с кодом {} не найдена", activationRequest.getCode());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Лицензия не найдена");
            }
            logger.info("Лицензия с кодом {} найдена", activationRequest.getCode());

            String email = jwtTokenProvider.getEmailFromRequest(request);
            Optional<ApplicationUser> userOptional = applicationUserRepository.findByEmail(email);
            ApplicationUser user = userOptional.get();
            if (license.getUser() != null) {
                if (!license.getUser().getEmail().equals(email)) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Ошибка");
                }
            } else {
                license.setUser(user);
            }

            // 3. Регистрация или обновление устройства
            Optional<Device> deviceOptional = deviceRepository.findByMacAddressAndName(activationRequest.getMacAddress(), activationRequest.getDeviceName());
            Device device;

            Optional<Device> existingDevice = deviceRepository.findByMacAddressAndName(activationRequest.getMacAddress(), activationRequest.getDeviceName());
            if (existingDevice.isPresent()) {
                logger.error("Устройство с MAC-адресом {} и именем {} уже существует", activationRequest.getMacAddress(), activationRequest.getDeviceName());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Устройство с таким MAC-адресом и именем уже существует");
            }

            if (deviceOptional.isPresent()) {
                device = deviceOptional.get();
                logger.info("Устройство найдено: {}", device);

            } else {
                // Если устройство не найдено, регистрируем его
                device = new Device();
                device.setMacAddress(activationRequest.getMacAddress());
                device.setName(activationRequest.getDeviceName());
                device.setUserId(license.getUser().getId());
                deviceRepository.save(device);
                logger.info("Устройство с MAC-адресом {} и именем {} зарегистрировано", activationRequest.getMacAddress(), activationRequest.getDeviceName());
            }

            // 5. Проверка доступных мест для активации устройства на лицензии
            if (license.getDeviceCount() <= 0) {
                logger.warn("Для лицензии с кодом {} нет доступных мест для активации", activationRequest.getCode());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Нет доступных мест для активации на этой лицензии");
            }

            // 6. Проверка активации лицензии
            Optional<DeviceLicense> existingDeviceLicenseOptional = deviceLicenseRepository.findByDeviceIdAndLicenseId(device.getId(), license.getId());

            if (existingDeviceLicenseOptional.isPresent()) {
                logger.warn("Лицензия с кодом {} уже активирована на устройстве с ID {}", activationRequest.getCode(), device.getId());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Лицензия уже активирована на данном устройстве");
            }

            // 7. Активация лицензии на устройстве
            DeviceLicense deviceLicense = new DeviceLicense();
            deviceLicense.setLicenseId(license.getId());
            deviceLicense.setDeviceId(device.getId());
            deviceLicense.setActivationDate(new Date());
            deviceLicenseRepository.save(deviceLicense);
            logger.info("Лицензия с кодом {} активирована на устройстве с ID {}", activationRequest.getCode(), device.getId());

            // 8. Обновляем количество доступных мест на лицензии
            license.setDeviceCount(license.getDeviceCount() - 1);
            licenseRepository.save(license);
            logger.info("Количество доступных мест для активации на лицензии с кодом {} уменьшено на 1", activationRequest.getCode());

            // 9. Запись в историю лицензий
            String description = "Лицензия активирована на устройстве";
            Date changeDate = new Date();
            licenseHistoryService.recordLicenseChange(license.getId(), license.getUser().getId(), "Активирована", changeDate, description);
            logger.info("Запись изменений лицензии в историю завершена");

            // 10. Создаем тикет для успешной активации
            Ticket ticket = Ticket.createTicket(null, false, license.getEndingDate());
            logger.info("Тикет с подтверждением активации лицензии создан: {}", ticket);

            return ResponseEntity.status(HttpStatus.OK).body("Лицензия успешно активирована на устройстве");

        } catch (Exception e) {
            logger.error("Произошла ошибка при активации лицензии: {}", e.getMessage(), e);
            // Создаем тикет с ошибкой
            Ticket ticket = Ticket.createTicket(null, true, null);
            logger.info("Тикет с ошибкой: {}", ticket);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Произошла ошибка при активации лицензии");
        }
    }


    // Метод для генерации активационного кода (можно улучшить по необходимости)
    private String generateActivationCode() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
