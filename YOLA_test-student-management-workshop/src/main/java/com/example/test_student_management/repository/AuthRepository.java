package com.example.test_student_management.repository;

import com.example.test_student_management.util.Database;
import com.example.test_student_management.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthRepository {
    public boolean usernameExists(String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean register(String username, String rawPassword) throws SQLException {
        if (usernameExists(username)) {
            return false;
        }
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, PasswordUtil.hash(rawPassword));
            statement.executeUpdate();
            return true;
        }
    }

    public boolean authenticate(String username, String rawPassword) throws SQLException {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                return PasswordUtil.hash(rawPassword).equals(rs.getString("password_hash"));
            }
        }
    }
}
