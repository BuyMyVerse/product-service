package com.demo.api.service;

import com.demo.api.model.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Optional;

@Service
public class UserService {

    private final List<User> users = new ArrayList<>();

    public UserService() {
        users.add(new User(1L, "Alice Johnson",   "alice@example.com",   "Admin",    "+1-202-555-0101"));
        users.add(new User(2L, "Bob Smith",        "bob@example.com",     "User",     "+1-202-555-0102"));
        users.add(new User(3L, "Carol Williams",   "carol@example.com",   "Manager",  "+1-202-555-0103"));
        users.add(new User(4L, "David Brown",      "david@example.com",   "User",     "+1-202-555-0104"));
        users.add(new User(5L, "Eva Martinez",     "eva@example.com",     "Editor",   "+1-202-555-0105"));
    }

    public List<User> getAllUsers() {
        return Collections.unmodifiableList(users);
    }

    public Optional<User> getUserById(Long id) {
        return users.stream()
                .filter(u -> u.getId().equals(id))
                .findFirst();
    }
}
