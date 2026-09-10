package test.entities;

import entities.*;

import org.junit.Test;
import static org.junit.Assert.*;

public class AdminTest {

    @Test
    public void testAdminCreation() {
        Admin admin = new Admin();
        assertNotNull(admin);
    }

    @Test
    public void testGetWelcomeMessage() {
        Admin admin = new Admin();
        String expected = "Login berhasil! Selamat datang, Burhan.";
        
        assertEquals(expected, admin.getWelcomeMessage());
    }

    @Test
    public void testGetters() {
        Admin admin = new Admin();

        assertEquals("Burhan", admin.getName());
        assertEquals("burhan", admin.getUsername());
    }

    @Test
    public void testAuthenticateSuccess() {
        Admin admin = new Admin();

        boolean result = admin.authenticate("burhan", "burunghantu123");

        assertTrue(result);
    }

    @Test
    public void testAuthenticateWrongUsername() {
        Admin admin = new Admin();

        boolean result = admin.authenticate("wrong", "burunghantu123");

        assertFalse(result);
    }

    @Test
    public void testAuthenticateWrongPassword() {
        Admin admin = new Admin();

        boolean result = admin.authenticate("burhan", "wrong");

        assertFalse(result);
    }

    @Test
    public void testAuthenticateBothWrong() {
        Admin admin = new Admin();

        boolean result = admin.authenticate("wrong", "wrong");

        assertFalse(result);
    }
}