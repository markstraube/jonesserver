package com.straube.jones.config;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.straube.jones.model.Permission;
import com.straube.jones.model.User;
import com.straube.jones.repository.PermissionRepository;
import com.straube.jones.repository.UserRepository;

@Configuration
public class DataInitializer
{

    @Autowired
    PermissionRepository permissionRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initData()
    {
        return args -> {
            // Init Permissions
            String[] perms = {"PORTFOLIO_READ", "PORTFOLIO_CREATE", "PORTFOLIO_UPDATE", "PORTFOLIO_DELETE",
                              "WATCHLIST_READ", "WATCHLIST_CREATE", "WATCHLIST_UPDATE", "WATCHLIST_DELETE",
                              "BOARD_READ", "BOARD_CREATE", "BOARD_UPDATE", "BOARD_DELETE",
                              "PORTFOLIO_EXECUTE_ADD", "WATCHLIST_EXECUTE_ADD", "ADMIN"};

            for (String permName : perms)
            {
                if (permissionRepository.findByName(permName).isEmpty())
                {
                    permissionRepository.save(new Permission(permName));
                }
            }

            // Init Admin
            if (userRepository.findByUsername("admin").isEmpty())
            {
                User admin = new User();
                admin.setUsername("admin");
                admin.setEmail("admin@example.com");
                admin.setPassword(passwordEncoder.encode("admin123")); // Default password
                admin.setActive(true);

                Set<Permission> adminPerms = new HashSet<>(permissionRepository.findAll());
                admin.setPermissions(adminPerms);

                userRepository.save(admin);
                System.out.println("Valid Default Admin User created: admin / admin123");
            }
        };
    }
}
