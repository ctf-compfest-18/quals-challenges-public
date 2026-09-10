package test.services;

import entities.User;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import services.AuthService;
import test.LoggerTestHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AuthServiceTest {
    private AuthService authService;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File logFile;

    @Before
    public void setUp() throws IOException {
        authService = new AuthService();
        logFile = LoggerTestHelper.isolateLogger(tempFolder);
    }

    @After
    public void tearDown() {
        LoggerTestHelper.resetLogger();
    }

    @Test
    public void testConstructorAddsDefaultAdminAndLoginWorks() {
        User admin = authService.login("burhan", "burunghantu123");

        assertNotNull(admin);
        assertTrue(authService.isAdmin(admin));
        assertTrue(authService.isUsernameTaken("burhan"));
        assertFalse(authService.isUniqueUsername("burhan"));
        assertNull(authService.login("burhan", "wrong"));
    }

    @Test
    public void testLoginFailureUsesSystemActorInLog() throws IOException {
        authService.login("charlie", "charlie123");

        String logContent = Files.readString(logFile.toPath());
        assertTrue(logContent.contains("[AUTH] [Sistem] - charlie Gagal login: Autentikasi tidak valid"));
    }

    @Test
    public void testInputValidationHelpers() {
        assertTrue(authService.isUsernameInputValid("user_123"));
        assertFalse(authService.isUsernameInputValid("user-name"));
        assertTrue(authService.isValidLoginInput("user_123", "pass"));
        assertFalse(authService.isValidLoginInput("user-name", "pass"));
        assertTrue(authService.isNameInputValid("Goblin King"));
        assertFalse(authService.isNameInputValid("goblin King"));
        assertTrue(authService.isSortOrderValid("DESC"));
        assertFalse(authService.isSortOrderValid("up"));
    }

    @Test
    public void testIsValidLoginInputRejectsNullOrEmptyUsernameAndPassword() {
        assertFalse(authService.isValidLoginInput(null, "pass"));
        assertFalse(authService.isValidLoginInput("", "pass"));
        assertFalse(authService.isValidLoginInput("user", null));
        assertFalse(authService.isValidLoginInput("user", ""));
        assertFalse(authService.isValidLoginInput("user-name", "pass"));
        assertTrue(authService.isValidLoginInput("user_123", "pass"));
    }

    @Test
    public void testValidateLoginInputAllowsValidInput() {
        authService.validateLoginInput("user_123", "pass");
    }

    @Test
    public void testValidateLoginInputRejectsInvalidInput() {
        assertInvalidLoginInput(null, "pass");
        assertInvalidLoginInput("user", null);
        assertInvalidLoginInput("user", "");
        assertInvalidLoginInput("user-name", "pass");
    }

    @Test
    public void testValidateSortOrderInputAllowsAscAndDescIgnoringCase() {
        authService.validateSortOrderInput("asc");
        authService.validateSortOrderInput("DESC");
    }

    @Test
    public void testValidateSortOrderInputRejectsInvalidInput() {
        assertInvalidSortOrder(null);
        assertInvalidSortOrder("");
        assertInvalidSortOrder("ascending");
        assertInvalidSortOrder("up");
    }

    private void assertInvalidLoginInput(String username, String password) {
        try {
            authService.validateLoginInput(username, password);
            fail("validateLoginInput seharusnya melempar IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            assertEquals("Input invalid, masukkan username dan password sesuai dengan ketentuan yang ada!", e.getMessage());
        }
    }

    private void assertInvalidSortOrder(String order) {
        try {
            authService.validateSortOrderInput(order);
            fail("validateSortOrderInput seharusnya melempar IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            assertEquals("Pilihan tidak valid. Harap masukkan pilihan dengan benar.", e.getMessage());
        }
    }
}
