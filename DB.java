package com.kangaroo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class DB {
    private static final String DB_FILE = System.getProperty("user.home") + "/.kangaroo/app.db";
    private static final String URL = "jdbc:sqlite:" + DB_FILE;

    // PBKDF2 parameters
    private static final String KDF_ALGO = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH = 256; // bits
    private static final int SALT_LENGTH = 16; // bytes

    public static void init() {
        try {
            Path p = Path.of(System.getProperty("user.home"), ".kangaroo");
            if (!Files.exists(p)) Files.createDirectories(p);
            try (Connection conn = DriverManager.getConnection(URL)) {
                try (Statement st = conn.createStatement()) {
                    st.execute("CREATE TABLE IF NOT EXISTS programs(id INTEGER PRIMARY KEY, code TEXT UNIQUE, name TEXT, description TEXT)");
                    st.execute("CREATE TABLE IF NOT EXISTS applications(id INTEGER PRIMARY KEY, full_name TEXT, nrc_number TEXT, program_id INTEGER, grade12_path TEXT, nrc_path TEXT, payment_id INTEGER, status TEXT DEFAULT 'draft', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                    st.execute("CREATE TABLE IF NOT EXISTS payments(id INTEGER PRIMARY KEY, amount REAL, reference TEXT, paid INTEGER DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                    // user table for authentication
                    st.execute("CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY, email TEXT UNIQUE, password_hash TEXT, salt TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                }
                // seed with requested programs (if empty)
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(1) FROM programs")) {
                    ResultSet rs = ps.executeQuery();
                    if (rs.next() && rs.getInt(1) == 0) {
                        try (PreparedStatement ins = conn.prepareStatement("INSERT INTO programs(code,name,description) VALUES(?,?,?)")) {
                            ins.setString(1, "IT01"); ins.setString(2, "Bachelor's Degree in Information Technology"); ins.setString(3, "Undergraduate degree in Information Technology"); ins.executeUpdate();
                            ins.setString(1, "SW01"); ins.setString(2, "Bachelor's Degree in Software"); ins.setString(3, "Undergraduate degree in Software Engineering and Development"); ins.executeUpdate();
                            ins.setString(1, "CYB01"); ins.setString(2, "Bachelor's Degree in Cyber Security"); ins.setString(3, "Undergraduate degree focusing on cybersecurity principles and practices"); ins.executeUpdate();
                        }
                    }
                }

                // seed a default user if none exists (for development/demo). Password: Password123
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(1) FROM users")) {
                    ResultSet rs = ps.executeQuery();
                    if (rs.next() && rs.getInt(1) == 0) {
                        createUser("user@example.com", "Password123");
                        System.out.println("Seeded default user: user@example.com with password Password123");
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<Map<String, Object>> getPrograms() {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(URL)) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT id,code,name,description FROM programs ORDER BY name")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("code", rs.getString("code"));
                    m.put("name", rs.getString("name"));
                    m.put("description", rs.getString("description"));
                    out.add(m);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return out;
    }

    public static int createPayment(double amount, String reference, boolean paid) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payments(amount,reference,paid) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setDouble(1, amount);
                ps.setString(2, reference);
                ps.setInt(3, paid ? 1 : 0);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) return keys.getInt(1);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return -1;
    }

    public static int createApplication(String fullName, String nrc, int programId, String gradePath, String nrcPath) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO applications(full_name,nrc_number,program_id,grade12_path,nrc_path,status) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, fullName);
                ps.setString(2, nrc);
                ps.setInt(3, programId);
                ps.setString(4, gradePath);
                ps.setString(5, nrcPath);
                ps.setString(6, "submitted");
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) return keys.getInt(1);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return -1;
    }

    public static void linkPaymentToApplication(int appId, int paymentId) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE applications SET payment_id=? WHERE id=?")) {
                ps.setInt(1, paymentId);
                ps.setInt(2, appId);
                ps.executeUpdate();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ----------------- User authentication helpers -----------------
    private static byte[] generateSalt() {
        SecureRandom rnd = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        rnd.nextBytes(salt);
        return salt;
    }

    private static String hashPassword(String password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(KDF_ALGO);
        byte[] hash = skf.generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(hash);
    }

    public static boolean createUser(String email, String password) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            byte[] salt = generateSalt();
            String saltB64 = Base64.getEncoder().encodeToString(salt);
            String hash = hashPassword(password, salt);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO users(email,password_hash,salt) VALUES(?,?,?)")) {
                ps.setString(1, email);
                ps.setString(2, hash);
                ps.setString(3, saltB64);
                ps.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            // likely unique constraint violation
            // e.printStackTrace();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String authenticateUser(String email, String password) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT password_hash,salt FROM users WHERE email = ?")) {
                ps.setString(1, email);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    String saltB64 = rs.getString("salt");
                    byte[] salt = Base64.getDecoder().decode(saltB64);
                    String attempted = hashPassword(password, salt);
                    // constant-time comparison
                    if (MessageDigest.isEqual(Base64.getDecoder().decode(storedHash), Base64.getDecoder().decode(attempted))) {
                        return email; // authentication success, return identifier
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
