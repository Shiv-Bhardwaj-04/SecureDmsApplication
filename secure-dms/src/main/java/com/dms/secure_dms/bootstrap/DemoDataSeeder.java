package com.dms.secure_dms.bootstrap;

import com.dms.secure_dms.entity.Role;
import com.dms.secure_dms.entity.User;
import com.dms.secure_dms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Mirrors the real-world flow: an admin creates accounts, nobody self-registers.
// This just automates that one-time setup step so you can log in immediately
// instead of building a registration screen you'll never actually need.
@Component
public class DemoDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DemoDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          @Value("${dms.demo.password:Demo@123}") String demoPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        createUser("Anita Sharma", "anita@dms.gov.in", Role.ADMIN);
        createUser("Priya Nair", "priya@dms.gov.in", Role.IO);
        createUser("Karan Mehta", "karan@dms.gov.in", Role.REVIEWER);
        createUser("Vikram Rao", "vikram@dms.gov.in", Role.AUDITOR);

        System.out.println("Seeded 4 demo users. Use the configured demo password.");
    }

    private void createUser(String fullName, String email, Role role) {
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(demoPassword));
        user.setRole(role);
        userRepository.save(user);
    }
}
