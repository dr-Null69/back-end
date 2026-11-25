package com.example.anml.web;

import java.util.List;

public record CurrentUserInfo(
        boolean authenticated,
        String username,
        List<String> roles
) {

    public static CurrentUserInfo anonymous() {
        return new CurrentUserInfo(false, "anonymous", List.of());
    }
}
