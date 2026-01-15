package com.example.TelegramWordsBot.service;

import com.example.TelegramWordsBot.dto.UserState;
import com.example.TelegramWordsBot.model.User;
import com.example.TelegramWordsBot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${bot.secret_key}")
    private String secretKey;

    @Transactional
    public User findOrCreate(Long chatId) {
        return userRepository.findByChatId(chatId)
                .orElseGet(() ->
                        userRepository.save(
                                new User(chatId, UserState.WAITING_FOR_AUTH_KEY, null, null)
                        )
                );
    }

    @Transactional
    public void setState(Long chatId, UserState state) {
        User user = findOrCreate(chatId);
        user.setUserState(state);
        userRepository.save(user);
    }

    @Transactional
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public boolean authorize(Long chatId, String inputKey) {
        User user = findOrCreate(chatId);

        if (passwordEncoder.matches(inputKey, secretKey)) {
            user.setAuthKey(inputKey);
            user.setUserState(UserState.IDLE);
            userRepository.save(user);
            return true;
        }

        return false;
    }

    @Transactional(readOnly = true)
    public boolean isAuthorized(User user) {
        return user.getAuthKey() != null
                && user.getAuthKey().equals(secretKey);
    }
}


