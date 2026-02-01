package com.straube.jones.controller;


import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.straube.jones.model.Permission;
import com.straube.jones.model.User;
import com.straube.jones.repository.PermissionRepository;
import com.straube.jones.repository.UserRepository;
import com.straube.jones.dto.auth.MessageResponse;
import com.straube.jones.dto.auth.SignupRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "User Management", description = "Administrative endpoints for managing users and permissions (ADMIN only)")
public class UserManagementController
{

    @Autowired
    UserRepository userRepository;

    @Autowired
    PermissionRepository permissionRepository;

    @Autowired
    PasswordEncoder encoder;

    @Operation(summary = "Get all permissions", description = "Retrieves a list of all available permissions.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List of permissions", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Permission.class)))),
                           @ApiResponse(responseCode = "403", description = "Access denied (Requires ADMIN role)", content = @Content)})
    @GetMapping("/permissions")
    public List<Permission> getAllPermissions()
    {
        return permissionRepository.findAll();
    }


    @Operation(summary = "Get all users", description = "Retrieves a list of all registered users.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List of users", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = User.class)))),
                           @ApiResponse(responseCode = "403", description = "Access denied (Requires ADMIN role)", content = @Content)})
    @GetMapping
    public List<User> getAllUsers()
    {
        return userRepository.findAll();
    }


    @Operation(summary = "Create user", description = "Creates a new user with the specified details and permissions.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "User created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponse.class))),
                           @ApiResponse(responseCode = "400", description = "Bad Request (Username or Email already exists)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponse.class))),
                           @ApiResponse(responseCode = "403", description = "Access denied (Requires ADMIN role)", content = @Content)})
    @PostMapping
    public ResponseEntity< ? > createUser(@RequestBody
    SignupRequest request)
    {
        if (userRepository.existsByUsername(request.getUsername()))
        { return ResponseEntity.badRequest().body(new MessageResponse("Error: Username is already taken!")); }
        if (userRepository.existsByEmail(request.getEmail()))
        { return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!")); }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(encoder.encode(request.getPassword()));
        user.setActive(true);

        Set<String> strPermissions = request.getPermissions();
        Set<Permission> permissions = new HashSet<>();

        if (strPermissions != null)
        {
            strPermissions.forEach(permissionName -> {
                Permission permission = permissionRepository.findByName(permissionName)
                                                            .orElseThrow(() -> new RuntimeException("Error: Permission is not found: "
                                                                            + permissionName));
                permissions.add(permission);
            });
        }
        user.setPermissions(permissions);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("User created successfully!"));
    }


    @Operation(summary = "Update user", description = "Updates details (username, email, active status) of an existing user.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "User updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponse.class))),
                           @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
                           @ApiResponse(responseCode = "403", description = "Access denied (Requires ADMIN role)", content = @Content)})
    @PutMapping("/{id}")
    public ResponseEntity< ? > updateUser(@Parameter(description = "ID of the user to update")
    @PathVariable
    Long id, @RequestBody
    User userDetails)
    {
        return userRepository.findById(id).map(user -> {
            user.setUsername(userDetails.getUsername());
            user.setEmail(userDetails.getEmail());
            user.setActive(userDetails.isActive());

            // Handle permissions update if provided
            if (userDetails.getPermissions() != null)
            {
                Set<Permission> newPermissions = new HashSet<>();
                for (Permission p : userDetails.getPermissions())
                {
                    if (p.getName() != null)
                    {
                        permissionRepository.findByName(p.getName()).ifPresent(newPermissions::add);
                    }
                }
                user.setPermissions(newPermissions);
            }

            userRepository.save(user);
            return ResponseEntity.ok(new MessageResponse("User updated successfully!"));
        }).orElse(ResponseEntity.notFound().build());
    }


    @Operation(summary = "Delete user", description = "Deletes a user by ID.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "User deleted successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponse.class))),
                           @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
                           @ApiResponse(responseCode = "403", description = "Access denied (Requires ADMIN role)", content = @Content)})
    @DeleteMapping("/{id}")
    public ResponseEntity< ? > deleteUser(@Parameter(description = "ID of the user to delete")
    @PathVariable
    Long id)
    {
        return userRepository.findById(id).map(user -> {
            if (user.getUsername().equalsIgnoreCase("admin") || user.getId() == 1L)
            {
                return ResponseEntity.badRequest()
                                     .body(new MessageResponse("Error: Cannot delete admin user!"));
            }
            userRepository.delete(user);
            return ResponseEntity.ok(new MessageResponse("User deleted successfully!"));
        }).orElse(ResponseEntity.notFound().build());
    }


    @Operation(summary = "Get user permissions", description = "Retrieves permissions for a specific user.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Set of permissions", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Permission.class)))),
                           @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
                           @ApiResponse(responseCode = "403", description = "Access denied (Requires ADMIN role)", content = @Content)})
    @GetMapping("/{id}/permissions")
    public ResponseEntity< ? > getUserPermissions(@Parameter(description = "ID of the user")
    @PathVariable
    Long id)
    {
        return userRepository.findById(id).map(user -> {
            return ResponseEntity.ok(user.getPermissions());
        }).orElse(ResponseEntity.notFound().build());
    }


    @Operation(summary = "Update user permissions", description = "Updates the set of permissions for a specific user.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Permissions updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponse.class))),
                           @ApiResponse(responseCode = "404", description = "User not found", content = @Content),
                           @ApiResponse(responseCode = "403", description = "Access denied (Requires ADMIN role)", content = @Content)})
    @PutMapping("/{id}/permissions")
    public ResponseEntity< ? > updateUserPermissions(@Parameter(description = "ID of the user")
    @PathVariable
    Long id, @RequestBody
    Set<String> permissionNames)
    {
        return userRepository.findById(id).map(user -> {
            Set<Permission> permissions = new HashSet<>();
            permissionNames.forEach(name -> {
                Permission p = permissionRepository.findByName(name)
                                                   .orElseThrow(() -> new RuntimeException("Permission not found: "
                                                                   + name));
                permissions.add(p);
            });
            user.setPermissions(permissions);
            userRepository.save(user);
            return ResponseEntity.ok(new MessageResponse("Permissions updated successfully!"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
