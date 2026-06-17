package com.vdt.soc.tenant.config;

import com.vdt.soc.common.enumeration.UserRole;
import com.vdt.soc.tenant.enumeration.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Giả định bạn đã có TenantUser (hoặc User) entity và UserRepository tương ứng
import com.vdt.soc.tenant.entity.User;
import com.vdt.soc.tenant.repository.UserRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String adminUsername = "admin";

        if (!userRepository.existsByUsername(adminUsername)) {
            log.info("Not found default account -> create");

            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setEmail("admin@eps.viettel.vn");
            admin.setPasswordHash(passwordEncoder.encode("admin123"));
            admin.setFullName("Admin");


            admin.setRole(UserRole.SYSTEM_ADMIN);
            admin.setStatus(UserStatus.ACTIVE);

            // 3. Lưu vào cơ sở dữ liệu
            userRepository.save(admin);
            log.info("created default account successfully!");
            log.info("Username: admin | Password: admin123");
        } else {
            log.info("Account already exists!");
        }
    }
}