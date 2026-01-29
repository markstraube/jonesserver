package com.straube.jones.controller;


import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.straube.jones.dto.auth.JwtResponse;
import com.straube.jones.dto.auth.LoginRequest;
import com.straube.jones.dto.auth.MessageResponse;
import com.straube.jones.dto.auth.SignupRequest;
import com.straube.jones.dto.auth.ChangePasswordRequest;
import com.straube.jones.model.Permission;
import com.straube.jones.model.User;
import com.straube.jones.repository.PermissionRepository;
import com.straube.jones.repository.UserRepository;
import com.straube.jones.security.jwt.JwtUtils;
import com.straube.jones.security.services.UserDetailsImpl;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Security", description = "Endpoints for Authentication (Login) and Registration")
public class AuthController
{
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PermissionRepository permissionRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Operation(summary = "User Login", description = "Authenticates a user via username and password. Returns a JWT token to be used for subsequent requests.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Authentication successful", content = @Content(mediaType = "application/json", schema = @Schema(implementation = JwtResponse.class))),
                           @ApiResponse(responseCode = "401", description = "Authentication failed (Bad credentials)", content = @Content)})
    @PostMapping("/login")
    public ResponseEntity< ? > authenticateUser(@Valid
    @RequestBody
    LoginRequest loginRequest)
    {

        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getUsername(),
                                                                                                                   loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl)authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities()
                                        .stream()
                                        .map(item -> item.getAuthority())
                                        .collect(Collectors.toList());

        return ResponseEntity.ok(new JwtResponse(jwt,
                                                 userDetails.getId(),
                                                 userDetails.getUsername(),
                                                 userDetails.getEmail(),
                                                 roles));
    }


    @Operation(summary = "User Registration", description = "Registers a new user with username, password, email and optional permissions.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "User registered successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponse.class))),
                           @ApiResponse(responseCode = "400", description = "Bad Request (e.g. Username or Email already exists)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponse.class)))})
    @PostMapping("/register")
    public ResponseEntity< ? > registerUser(@Valid
    @RequestBody
    SignupRequest signUpRequest)
    {
        if (userRepository.existsByUsername(signUpRequest.getUsername()))
        { return ResponseEntity.badRequest().body(new MessageResponse("Error: Username is already taken!")); }

        if (userRepository.existsByEmail(signUpRequest.getEmail()))
        { return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!")); }

        // Create new user's account
        User user = new User();
        user.setUsername(signUpRequest.getUsername());
        user.setEmail(signUpRequest.getEmail());
        user.setPassword(encoder.encode(signUpRequest.getPassword()));
        user.setActive(true);

        Set<String> strPermissions = signUpRequest.getPermissions();
        Set<Permission> permissions = new HashSet<>();

        if (strPermissions == null)
        {
            // Default permissions or empty? maybe PORTFOLIO_READ
        }
        else
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

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    @Operation(summary = "Change Password", description = "Allows the authenticated user to change their password.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password changed successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid current password", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Error: User not found."));

        if (!encoder.matches(request.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Invalid current password!"));
        }

        user.setPassword(encoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Password changed successfully!"));
    }
}
