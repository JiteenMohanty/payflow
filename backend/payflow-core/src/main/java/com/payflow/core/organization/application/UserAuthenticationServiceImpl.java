package com.payflow.core.organization.application;

import com.payflow.core.common.exception.ResourceNotFoundException;
import com.payflow.core.organization.domain.User;
import com.payflow.core.organization.domain.UserStatus;
import com.payflow.core.organization.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAuthenticationServiceImpl implements UserAuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> authenticate(String email, String rawPassword) {
        return userRepository.findByEmail(email)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .map(this::toAuthenticatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser getById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return toAuthenticatedUser(user);
    }

    private AuthenticatedUser toAuthenticatedUser(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getFullName());
    }
}
