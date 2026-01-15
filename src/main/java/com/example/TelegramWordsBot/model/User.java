package com.example.TelegramWordsBot.model;

import com.example.TelegramWordsBot.dto.UserState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    private Long chatId;
    @Enumerated(EnumType.STRING)
    private UserState userState;
    private String sheetId;
    private String authKey;
}
