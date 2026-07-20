package com.docai.shared;

import com.docai.auth.AuthRepository;
import com.docai.auth.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserResolver {

    private final AuthRepository authRepository;

    public User resolve(String email) {
        return authRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
