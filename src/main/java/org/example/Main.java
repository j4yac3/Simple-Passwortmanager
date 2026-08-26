package org.example;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.sql.*;
import java.util.Base64;

public class Main extends Application {

    private final VBox entriesContainer = new VBox(14);
    private final VBox usersContainer = new VBox(10);
    private static final String DB_URL;
    private static final java.io.File APP_DIR;
    private static Connection dbConn;

    private static synchronized Connection db() {
        try {
            if (dbConn == null || dbConn.isClosed()) {
                dbConn = DriverManager.getConnection(DB_URL);
                try (Statement st = dbConn.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL");
                    st.execute("PRAGMA synchronous=NORMAL");
                    st.execute("PRAGMA busy_timeout=5000");
                }
            }
        } catch (SQLException e) {
            return null;
        }
        return dbConn;
    }

    static {
        String userHome = System.getProperty("user.home");
        java.io.File appDir = new java.io.File(userHome, ".jm-passwortmanager");
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        APP_DIR = appDir;
        DB_URL = "jdbc:sqlite:" + new java.io.File(appDir, "vault.db").getAbsolutePath();
    }

    private String currentUser = "";
    private boolean isSidebarVisible = true;
    private boolean isDarkMode = true;

    private SecretKeySpec sessionKeySpec;
    private Stage primaryStage;
    private BorderPane mainLayout;
    private ScrollPane entriesScrollPane;
    private ScrollPane sidebarScrollPane;
    private Timeline autoLockTimer;
    private long lastActivityMillis;
    private Button langBtn;
    private Runnable currentAuthRedraw;

    private VBox sidebar;
    private Label menuTitle;
    private Label titleLabel;
    private VBox inputForm;
    private TextField platformInput;
    private TextField urlInput;
    private TextField totpInput;
    private TextField userInput;
    private TextField passwordInput;
    private Button generateBtn;
    private Region separator;
    private Button toggleSidebarBtn;
    private Button themeToggleBtn;
    private Button logoutBtn;
    private Button addBtn;
    private Button addUserBtn;

    private static final String ALGORITHM_CBC = "AES/CBC/PKCS5Padding";
    private static final String ALGORITHM_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int ITERATIONS = 600000;
    private static final int LEGACY_ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final long AUTO_LOCK_AFTER_MS = 5 * 60 * 1000L;
    private static final byte[] DPAPI_ENTROPY = "JM-Passwortmanager::SessionBinding::v1".getBytes(StandardCharsets.UTF_8);

    @Override
    public void start(Stage primaryStage) {
        try {
            startImpl(primaryStage);
        } catch (Throwable t) {
            ErrorLog.showFatal("Oberflaeche", t);
        }
    }

    private void startImpl(Stage primaryStage) {
        this.primaryStage = primaryStage;
        initDatabase();
        I18n.setLanguage(getConfig("language"));
        startWakeServer(this.primaryStage);

        try {
            InputStream iconStream = getClass().getResourceAsStream("/icon.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ignored) {}

        primaryStage.setTitle(I18n.tr("JM Passwortmanager"));
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        if (!isAppInitialized()) {
            showSetupScreen();
        } else if (tryRestoreSession()) {
            continueAfterUnlock();
        } else {
            showLoginScreen();
        }
    }

    private void startWakeServer(Stage stage) {
        Thread wakeThread = new Thread(() -> {
            try {
                java.net.ServerSocket ss = new java.net.ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), Launcher.WAKE_PORT));
                while (!ss.isClosed()) {
                    try (java.net.Socket s = ss.accept()) {
                        s.getInputStream().read();
                    }
                    javafx.application.Platform.runLater(() -> {
                        try {
                            if (stage.isIconified()) stage.setIconified(false);
                            stage.show();
                            stage.toFront();
                            stage.requestFocus();
                        } catch (Exception ignored) {}
                    });
                }
            } catch (Exception ignored) {}
        }, "wake-server");
        wakeThread.setDaemon(true);
        wakeThread.start();
    }

    private void toggleLanguage() {
        I18n.setLanguage(I18n.isEnglish() ? "de" : "en");
        setConfig("language", I18n.getLanguage());
        if (primaryStage != null) primaryStage.setTitle(I18n.tr("JM Passwortmanager"));
        Scene scene = primaryStage != null ? primaryStage.getScene() : null;
        if (scene != null && scene.getRoot() == mainLayout) {
            buildAndShowMainUI();
        } else if (currentAuthRedraw != null) {
            currentAuthRedraw.run();
        }
    }

    private void showSetupScreen() {
        VBox root = createAuthLayout(I18n.tr("JM Passwortmanager"), I18n.tr("Erstelle ein sicheres Master-Passwort, um deinen Tresor zu schützen."));

        PasswordField passInput = new PasswordField();
        passInput.setPromptText(I18n.tr("Master-Passwort"));
        styleAuthField(passInput);

        PasswordField passConfirm = new PasswordField();
        passConfirm.setPromptText(I18n.tr("Passwort bestätigen"));
        styleAuthField(passConfirm);

        Button submitBtn = createAuthButton(I18n.tr("Tresor initialisieren"));
        submitBtn.setOnAction(e -> performSetup(passInput, passConfirm));
        passInput.setOnAction(e -> passConfirm.requestFocus());
        passConfirm.setOnAction(e -> submitBtn.fire());

        root.getChildren().addAll(passInput, passConfirm, submitBtn);
        primaryStage.setScene(new Scene((StackPane) root.getUserData(), 1280, 720));
        primaryStage.show();
        primaryStage.centerOnScreen();
        currentAuthRedraw = this::showSetupScreen;
    }

    private void performSetup(PasswordField passInput, PasswordField passConfirm) {
        String p1 = passInput.getText();
        String p2 = passConfirm.getText();

        String validationError = validateMasterPassword(p1);
        if (validationError != null) {
            showAlert(validationError);
            return;
        }
        if (!p1.equals(p2)) {
            showAlert(I18n.tr("Die Passwörter stimmen nicht überein."));
            return;
        }

        try {
            String recoveryCode = generateStrongRecoveryCode();
            setupVaultSystem(p1, recoveryCode);
            showRecoveryCodeScreen(recoveryCode, this::buildAndShowMainUI);
        } catch (Exception ex) {
            showAlert(I18n.tr("Systemfehler bei der Initialisierung."));
        }
    }

    private void showLoginScreen() {
        VBox root = createAuthLayout(I18n.tr("Willkommen zurück"), I18n.tr("Bitte gib dein Master-Passwort ein, um den Tresor zu entsperren."));

        PasswordField passInput = new PasswordField();
        passInput.setPromptText(I18n.tr("Master-Passwort"));
        styleAuthField(passInput);

        CheckBox stayLoggedInBox = new CheckBox(I18n.tr("Angemeldet bleiben"));
        String stayNormal = "-fx-text-fill: #d4d4d8; -fx-font-size: 14px; -fx-cursor: hand; -fx-color: #3f3f46; -fx-padding: 12px 18px; -fx-background-color: #18181b; -fx-background-radius: 12px; -fx-border-color: #27272a; -fx-border-radius: 12px;";
        String stayHover = "-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-cursor: hand; -fx-color: #52525b; -fx-padding: 12px 18px; -fx-background-color: #1c1c1f; -fx-background-radius: 12px; -fx-border-color: #3f3f46; -fx-border-radius: 12px;";
        String staySelected = "-fx-text-fill: #10b981; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-color: #10b981; -fx-padding: 12px 18px; -fx-background-color: rgba(16,185,129,0.08); -fx-background-radius: 12px; -fx-border-color: #10b981; -fx-border-radius: 12px;";
        stayLoggedInBox.setMaxWidth(350);
        stayLoggedInBox.setStyle(stayNormal);
        stayLoggedInBox.setOnMouseEntered(e -> { if (!stayLoggedInBox.isSelected()) stayLoggedInBox.setStyle(stayHover); });
        stayLoggedInBox.setOnMouseExited(e -> { if (!stayLoggedInBox.isSelected()) stayLoggedInBox.setStyle(stayNormal); });
        stayLoggedInBox.selectedProperty().addListener((obs, was, is) -> stayLoggedInBox.setStyle(is ? staySelected : stayNormal));

        Button loginBtn = createAuthButton(I18n.tr("Entsperren"));
        loginBtn.setOnAction(e -> performLogin(passInput, stayLoggedInBox));
        passInput.setOnAction(e -> loginBtn.fire());

        Button forgotBtn = new Button(I18n.tr("Passwort vergessen?"));
        forgotBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #a1a1aa; -fx-cursor: hand; -fx-underline: true;");
        forgotBtn.setOnMouseEntered(e -> forgotBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffffff; -fx-cursor: hand; -fx-underline: true;"));
        forgotBtn.setOnMouseExited(e -> forgotBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #a1a1aa; -fx-cursor: hand; -fx-underline: true;"));
        forgotBtn.setOnAction(e -> showRecoveryScreen());

        root.getChildren().addAll(passInput, stayLoggedInBox, loginBtn, forgotBtn);
        primaryStage.setScene(new Scene((StackPane) root.getUserData(), 1280, 720));
        primaryStage.show();
        primaryStage.centerOnScreen();
        currentAuthRedraw = this::showLoginScreen;
    }

    private void performLogin(PasswordField passInput, CheckBox stayLoggedInBox) {
        if (tryUnlockWithPassword(passInput.getText())) {
            if (stayLoggedInBox.isSelected()) savePersistentSession();
            continueAfterUnlock();
        } else {
            showAlert(I18n.tr("Falsches Master-Passwort!"));
            passInput.clear();
            Platform.runLater(passInput::requestFocus);
        }
    }

    private void showRecoveryScreen() {
        VBox root = createAuthLayout(I18n.tr("Passwort zurücksetzen"), I18n.tr("Gib deinen Recovery-Code ein."));

        TextField codeInput = new TextField();
        codeInput.setPromptText(I18n.tr("Recovery-Code"));
        styleAuthField(codeInput);

        PasswordField newPassInput = new PasswordField();
        newPassInput.setPromptText(I18n.tr("Neues Master-Passwort"));
        styleAuthField(newPassInput);

        Button resetBtn = createAuthButton(I18n.tr("Passwort zurücksetzen"));
        resetBtn.setOnAction(e -> performRecoveryReset(codeInput, newPassInput));
        newPassInput.setOnAction(e -> resetBtn.fire());

        Button backBtn = new Button(I18n.tr("Zurück zum Login"));
        backBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #a1a1aa; -fx-cursor: hand;");
        backBtn.setOnAction(e -> showLoginScreen());

        root.getChildren().addAll(codeInput, newPassInput, resetBtn, backBtn);
        primaryStage.setScene(new Scene((StackPane) root.getUserData(), 1280, 720));
        currentAuthRedraw = this::showRecoveryScreen;
    }

    private void performRecoveryReset(TextField codeInput, PasswordField newPassInput) {
        String code = codeInput.getText() != null ? codeInput.getText().trim() : "";
        String newPass = newPassInput.getText() != null ? newPassInput.getText() : "";

        String validationError = validateMasterPassword(newPass);
        if (validationError != null) {
            showAlert(validationError);
            return;
        }

        if (tryUnlockWithRecoveryCodeAndReset(code, newPass)) {
            continueAfterUnlock();
        } else {
            showAlert(I18n.tr("Falscher oder ungültiger Recovery-Code."));
            newPassInput.clear();
            Platform.runLater(newPassInput::requestFocus);
        }
    }

    private void showRecoveryCodeScreen(String code, Runnable onComplete) {
        VBox root = createAuthLayout(I18n.tr("WICHTIG: Recovery-Code"), I18n.tr("Speichere diesen Code sicher ab!\nEr ist die einzige Möglichkeit, dein Passwort zurückzusetzen."));

        Label codeLabel = new Label(code);
        codeLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #10b981; -fx-letter-spacing: 3px;");

        Button copyBtn = new Button(I18n.tr("Code kopieren"));
        copyBtn.setStyle("-fx-background-color: #27272a; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 8; -fx-cursor: hand;");
        copyBtn.setOnAction(e -> {
            copyToClipboard(code);
            copyBtn.setText(I18n.tr("Kopiert!"));
            copyBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 8; -fx-cursor: hand;");
        });

        Button proceedBtn = createAuthButton(I18n.tr("Ich habe den Code sicher gespeichert"));
        proceedBtn.setOnAction(e -> onComplete.run());

        root.getChildren().addAll(codeLabel, copyBtn, proceedBtn);
        primaryStage.setScene(new Scene((StackPane) root.getUserData(), 1280, 720));
        currentAuthRedraw = () -> showRecoveryCodeScreen(code, onComplete);
    }

    private String validateMasterPassword(String password) {
        if (password == null || password.length() < 12) {
            return I18n.tr("Das Master-Passwort muss mindestens 12 Zeichen lang sein.");
        }
        if (!password.matches(".*[a-z].*") || !password.matches(".*[A-Z].*") || !password.matches(".*[0-9].*")) {
            return I18n.tr("Das Passwort braucht Groß- und Kleinbuchstaben sowie mindestens eine Zahl.");
        }
        return null;
    }

    private String generateStrongRecoveryCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder(28);
        for (int i = 0; i < 28; i++) {
            if (i > 0 && i % 7 == 0) code.append('-');
            code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    private void continueAfterUnlock() {
        if (!"true".equals(getConfig("recovery_strong"))) {
            try {
                String newCode = generateStrongRecoveryCode();
                byte[] newSalt = new byte[16];
                new SecureRandom().nextBytes(newSalt);
                byte[] kek = deriveKey(newCode, newSalt, ITERATIONS);
                String enc = encryptBytes(sessionKeySpec.getEncoded(), new SecretKeySpec(kek, "AES"));
                if (enc != null) {
                    setConfig("salt_recovery", Base64.getEncoder().encodeToString(newSalt));
                    setConfig("enc_vk_recovery", enc);
                    setConfig("iter_recovery", String.valueOf(ITERATIONS));
                    setConfig("recovery_strong", "true");
                    showRecoveryCodeScreen(newCode, this::buildAndShowMainUI);
                    return;
                }
            } catch (Exception ignored) {}
        }
        buildAndShowMainUI();
    }

    private void setupVaultSystem(String password, String recoveryCode) throws Exception {
        byte[] vaultKey = new byte[32];
        new SecureRandom().nextBytes(vaultKey);

        byte[] saltPass = new byte[16];
        new SecureRandom().nextBytes(saltPass);
        byte[] kekPass = deriveKey(password, saltPass, ITERATIONS);

        byte[] saltRecovery = new byte[16];
        new SecureRandom().nextBytes(saltRecovery);
        byte[] kekRecovery = deriveKey(recoveryCode, saltRecovery, ITERATIONS);

        String encryptedVaultKeyPass = encryptBytes(vaultKey, new SecretKeySpec(kekPass, "AES"));
        String encryptedVaultKeyRecovery = encryptBytes(vaultKey, new SecretKeySpec(kekRecovery, "AES"));

        setConfig("salt_pass", Base64.getEncoder().encodeToString(saltPass));
        setConfig("salt_recovery", Base64.getEncoder().encodeToString(saltRecovery));
        setConfig("enc_vk_pass", encryptedVaultKeyPass);
        setConfig("enc_vk_recovery", encryptedVaultKeyRecovery);
        setConfig("iter_pass", String.valueOf(ITERATIONS));
        setConfig("iter_recovery", String.valueOf(ITERATIONS));
        setConfig("recovery_strong", "true");
        setConfig("initialized", "true");

        this.sessionKeySpec = new SecretKeySpec(vaultKey, "AES");
    }

    private boolean tryUnlockWithPassword(String password) {
        if (password == null || password.isEmpty()) return false;
        try {
            byte[] saltPass = Base64.getDecoder().decode(getConfig("salt_pass"));
            String encVkPass = getConfig("enc_vk_pass");
            int iterations = getIntConfig("iter_pass", LEGACY_ITERATIONS);

            byte[] kekPass = deriveKey(password, saltPass, iterations);
            byte[] vaultKey = decryptBytes(encVkPass, new SecretKeySpec(kekPass, "AES"));

            if (vaultKey != null) {
                this.sessionKeySpec = new SecretKeySpec(vaultKey, "AES");
                if (iterations < ITERATIONS) upgradeWrapping(password, "pass");
                return true;
            }
        } catch (Exception e) { return false; }
        return false;
    }

    private boolean tryUnlockWithRecoveryCodeAndReset(String code, String newPassword) {
        if (code == null || code.isEmpty() || newPassword == null) return false;
        try {
            byte[] saltRecovery = Base64.getDecoder().decode(getConfig("salt_recovery"));
            String encVkRecovery = getConfig("enc_vk_recovery");
            int iterations = getIntConfig("iter_recovery", LEGACY_ITERATIONS);

            byte[] kekRecovery = deriveKey(code, saltRecovery, iterations);
            byte[] vaultKey = decryptBytes(encVkRecovery, new SecretKeySpec(kekRecovery, "AES"));

            if (vaultKey == null) return false;

            this.sessionKeySpec = new SecretKeySpec(vaultKey, "AES");

            byte[] newSaltPass = new byte[16];
            new SecureRandom().nextBytes(newSaltPass);
            byte[] newKekPass = deriveKey(newPassword, newSaltPass, ITERATIONS);
            String newEncVkPass = encryptBytes(vaultKey, new SecretKeySpec(newKekPass, "AES"));

            setConfig("salt_pass", Base64.getEncoder().encodeToString(newSaltPass));
            setConfig("enc_vk_pass", newEncVkPass);
            setConfig("iter_pass", String.valueOf(ITERATIONS));

            upgradeWrapping(code, "recovery");
            return true;
        } catch (Exception e) { return false; }
    }

    private void upgradeWrapping(String secretText, String kind) {
        try {
            byte[] newSalt = new byte[16];
            new SecureRandom().nextBytes(newSalt);
            byte[] kek = deriveKey(secretText, newSalt, ITERATIONS);
            String enc = encryptBytes(sessionKeySpec.getEncoded(), new SecretKeySpec(kek, "AES"));
            if (enc == null) return;
            setConfig("salt_" + kind, Base64.getEncoder().encodeToString(newSalt));
            setConfig("enc_vk_" + kind, enc);
            setConfig("iter_" + kind, String.valueOf(ITERATIONS));
        } catch (Exception ignored) {}
    }

    private int getIntConfig(String key, int defaultValue) {
        try {
            return Integer.parseInt(getConfig(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void savePersistentSession() {
        if (sessionKeySpec == null) return;
        try {
            byte[] protectedKey = protectData(sessionKeySpec.getEncoded());
            if (protectedKey != null) {
                java.nio.file.Files.write(new java.io.File(APP_DIR, "session.key").toPath(),
                        Base64.getEncoder().encodeToString(protectedKey).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    private boolean tryRestoreSession() {
        try {
            java.io.File f = new java.io.File(APP_DIR, "session.key");
            if (!f.exists()) return false;
            byte[] raw = java.nio.file.Files.readAllBytes(f.toPath());
            byte[] protectedKey = Base64.getDecoder().decode(new String(raw, StandardCharsets.UTF_8).trim());
            byte[] vaultKey = unprotectData(protectedKey);
            if (vaultKey != null && vaultKey.length == 32) {
                this.sessionKeySpec = new SecretKeySpec(vaultKey, "AES");
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void clearPersistentSession() {
        Connection conn = db();
        if (conn != null) {
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM config WHERE key = 'auto_login'")) {
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }
        try {
            java.nio.file.Files.deleteIfExists(new java.io.File(APP_DIR, "session.key").toPath());
        } catch (Exception ignored) {}
    }

    private byte[] getOrCreateMachineKey() throws Exception {
        java.io.File f = new java.io.File(APP_DIR, "machine.key");
        if (f.exists()) {
            byte[] raw = java.nio.file.Files.readAllBytes(f.toPath());
            return Base64.getDecoder().decode(new String(raw, StandardCharsets.UTF_8).trim());
        }
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        java.nio.file.Files.write(f.toPath(), Base64.getEncoder().encodeToString(key).getBytes(StandardCharsets.UTF_8));
        return key;
    }

    private byte[] protectData(byte[] data) {
        try {
            return com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(data, DPAPI_ENTROPY, 0, null, null);
        } catch (Throwable t) {
            try {
                byte[] machineKey = getOrCreateMachineKey();
                String enc = encryptBytes(data, new SecretKeySpec(machineKey, "AES"));
                return enc == null ? null : Base64.getDecoder().decode(enc);
            } catch (Exception e) { return null; }
        }
    }

    private byte[] unprotectData(byte[] blob) {
        try {
            return com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(blob, DPAPI_ENTROPY, 0, null);
        } catch (Throwable t) {
            try {
                byte[] machineKey = getOrCreateMachineKey();
                return decryptBytes(Base64.getEncoder().encodeToString(blob), new SecretKeySpec(machineKey, "AES"));
            } catch (Exception e) { return null; }
        }
    }

    private void performLogout() {
        stopAllTotpTimelines();
        stopAutoLockTimer();
        clearPersistentSession();
        sessionKeySpec = null;
        currentUser = "";
        entriesContainer.getChildren().clear();
        usersContainer.getChildren().clear();
        showLoginScreen();
    }

    private void performLock() {
        stopAllTotpTimelines();
        stopAutoLockTimer();
        sessionKeySpec = null;
        currentUser = "";
        entriesContainer.getChildren().clear();
        usersContainer.getChildren().clear();
        showLoginScreen();
    }

    private void startAutoLockTimer() {
        stopAutoLockTimer();
        lastActivityMillis = System.currentTimeMillis();
        autoLockTimer = new Timeline(new KeyFrame(Duration.seconds(15), e -> {
            Scene scene = primaryStage != null ? primaryStage.getScene() : null;
            boolean mainUiShown = scene != null && scene.getRoot() == mainLayout;
            if (mainUiShown && sessionKeySpec != null
                    && System.currentTimeMillis() - lastActivityMillis > AUTO_LOCK_AFTER_MS) {
                performLock();
            }
        }));
        autoLockTimer.setCycleCount(Animation.INDEFINITE);
        autoLockTimer.play();
    }

    private void stopAutoLockTimer() {
        if (autoLockTimer != null) {
            autoLockTimer.stop();
            autoLockTimer = null;
        }
    }

    private void stopTotpTimeline(StackPane card) {
        if (card.getUserData() instanceof Timeline tl) tl.stop();
    }

    private void stopAllTotpTimelines() {
        for (Node node : entriesContainer.getChildren()) {
            if (node instanceof StackPane card) stopTotpTimeline(card);
        }
    }

    private byte[] deriveKey(String password, byte[] salt, int iterations) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }

    private String encryptBytes(byte[] data, SecretKeySpec key) {
        if (data == null || key == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] enc = cipher.doFinal(data);
            byte[] combined = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(enc, 0, combined, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) { return null; }
    }

    private byte[] decryptBytes(String dataBase64, SecretKeySpec key) {
        if (dataBase64 == null || dataBase64.isEmpty() || key == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(dataBase64);
            if (combined.length > GCM_IV_LENGTH) {
                try {
                    GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, combined, 0, GCM_IV_LENGTH);
                    Cipher gcm = Cipher.getInstance(ALGORITHM_GCM);
                    gcm.init(Cipher.DECRYPT_MODE, key, gcmSpec);
                    return gcm.doFinal(combined, GCM_IV_LENGTH, combined.length - GCM_IV_LENGTH);
                } catch (Exception ignored) {}
            }
            int cbcIvLength = 16;
            if (combined.length > cbcIvLength) {
                IvParameterSpec ivSpec = new IvParameterSpec(combined, 0, cbcIvLength);
                Cipher cbc = Cipher.getInstance(ALGORITHM_CBC);
                cbc.init(Cipher.DECRYPT_MODE, key, ivSpec);
                return cbc.doFinal(combined, cbcIvLength, combined.length - cbcIvLength);
            }
        } catch (Exception e) { return null; }
        return null;
    }

    private void buildAndShowMainUI() {
        mainLayout = new BorderPane();

        sidebar = new VBox(20);
        sidebar.setPadding(new Insets(25, 15, 25, 15));
        sidebar.setPrefWidth(240);

        HBox sidebarHeader = new HBox();
        sidebarHeader.setAlignment(Pos.CENTER_LEFT);
        menuTitle = new Label("WORKSPACE");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        addUserBtn = new Button("＋");
        addUserBtn.setOnAction(e -> showAddUserDialog());
        sidebarHeader.getChildren().addAll(menuTitle, headerSpacer, addUserBtn);

        ScrollPane userScrollPane = new ScrollPane(usersContainer);
        sidebarScrollPane = userScrollPane;
        userScrollPane.setFitToWidth(true);
        userScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-control-inner-background: transparent; -fx-vbar-policy: as-needed; -fx-hbar-policy: never;");
        sidebar.getChildren().addAll(sidebarHeader, userScrollPane);
        mainLayout.setLeft(sidebar);

        VBox centerContainer = new VBox(24);
        centerContainer.setPadding(new Insets(25, 35, 25, 35));

        HBox topHeader = new HBox(15);
        topHeader.setAlignment(Pos.CENTER_LEFT);
        toggleSidebarBtn = new Button("☰");
        toggleSidebarBtn.setPrefSize(40, 40);
        toggleSidebarBtn.setOnAction(e -> {
            isSidebarVisible = !isSidebarVisible;
            mainLayout.setLeft(isSidebarVisible ? sidebar : null);
        });

        Label iconLabel = new Label("🔒");
        iconLabel.setStyle("-fx-font-size: 24px;");
        titleLabel = new Label(I18n.tr("Passwortmanager"));

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        themeToggleBtn = new Button();
        themeToggleBtn.setMinSize(40, 40);
        themeToggleBtn.setPrefSize(40, 40);
        themeToggleBtn.setPadding(new Insets(0));
        themeToggleBtn.setOnAction(e -> {
            isDarkMode = !isDarkMode;
            applyTheme();
        });

        logoutBtn = new Button("⏻");
        logoutBtn.setMinSize(40, 40);
        logoutBtn.setPrefSize(40, 40);
        logoutBtn.setPadding(new Insets(0));
        logoutBtn.setOnAction(e -> performLogout());

        langBtn = new Button(I18n.isEnglish() ? "DE" : "EN");
        langBtn.setMinSize(40, 40);
        langBtn.setPrefSize(40, 40);
        langBtn.setPadding(new Insets(0));
        langBtn.setOnAction(e -> toggleLanguage());

        topHeader.getChildren().addAll(toggleSidebarBtn, iconLabel, titleLabel, topSpacer, themeToggleBtn, langBtn, logoutBtn);

        inputForm = new VBox(14);
        inputForm.setPadding(new Insets(24));
        platformInput = createStyledTextField(I18n.tr("Plattform (z.B. Google, GitHub)"));
        urlInput = createStyledTextField(I18n.tr("Website-Link (z.B. https://github.com) - Optional"));
        totpInput = createStyledTextField(I18n.tr("2FA-Schlüssel (optional, für Login-Codes)"));
        Button totpHelpBtn = new Button("?");
        totpHelpBtn.setPrefSize(46, 46);
        totpHelpBtn.setMinSize(46, 46);
        applyButtonStyle(totpHelpBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #38bdf8; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 12px;",
                "-fx-background-color: #27272a; -fx-text-fill: #7dd3fc; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 12px;",
                "-fx-background-color: #ffffff; -fx-text-fill: #0284c7; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 12px;",
                "-fx-background-color: #f4f4f5; -fx-text-fill: #0369a1; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #d4d4d8; -fx-border-radius: 12px;"
        );
        setupIndividualButtonHover(totpHelpBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #38bdf8; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 12px;",
                "-fx-background-color: #27272a; -fx-text-fill: #7dd3fc; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 12px;",
                "-fx-background-color: #ffffff; -fx-text-fill: #0284c7; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 12px;",
                "-fx-background-color: #f4f4f5; -fx-text-fill: #0369a1; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #d4d4d8; -fx-border-radius: 12px;"
        );
        totpHelpBtn.setOnAction(e -> showTotpHelpDialog());
        HBox totpBox = new HBox(10);
        HBox.setHgrow(totpInput, Priority.ALWAYS);
        totpBox.getChildren().addAll(totpInput, totpHelpBtn);
        userInput = createStyledTextField(I18n.tr("Benutzername / E-Mail"));

        HBox passwordBox = new HBox(10);
        passwordInput = createStyledTextField(I18n.tr("Passwort"));
        HBox.setHgrow(passwordInput, Priority.ALWAYS);

        generateBtn = new Button("🎲");
        generateBtn.setPrefSize(46, 46);
        generateBtn.setMinSize(46, 46);
        generateBtn.setOnAction(e -> showPasswordGeneratorDialog(passwordInput));

        passwordBox.getChildren().addAll(passwordInput, generateBtn);

        addBtn = new Button();
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setOnAction(e -> {
            if (currentUser.isEmpty()) {
                showAddUserDialog();
                return;
            }

            String platform = platformInput.getText() != null ? platformInput.getText().trim() : "";
            String url = urlInput.getText() != null ? urlInput.getText().trim() : "";
            String totp = TotpUtil.normalize(totpInput.getText());
            String user = userInput.getText() != null ? userInput.getText().trim() : "";
            String pass = passwordInput.getText() != null ? passwordInput.getText().trim() : "";

            if (!totp.isEmpty() && !TotpUtil.isPlausible(totp)) {
                showAlert(I18n.tr("Das 2FA-Secret ist ungültig.\nErlaubt ist ein Base32-Secret oder ein otpauth://-Link."));
                totpInput.requestFocus();
                return;
            }

            if (!platform.isEmpty() && !user.isEmpty() && !pass.isEmpty()) {
                int id = saveToDatabase(currentUser, platform, url, user, pass, totp);
                if (id != -1) {
                    StackPane newCard = createSwipeableCard(id, platform, url, user, pass, totp);
                    entriesContainer.getChildren().add(0, newCard);
                    refreshEmptyEntriesHint();
                    platformInput.clear();
                    urlInput.clear();
                    totpInput.clear();
                    userInput.clear();
                    passwordInput.clear();
                }
            } else {
                showAlert(I18n.tr("Plattform, Benutzername und Passwort dürfen nicht leer sein."));
            }
        });

        inputForm.getChildren().addAll(platformInput, urlInput, totpBox, userInput, passwordBox, addBtn);

        platformInput.setOnAction(e -> urlInput.requestFocus());
        urlInput.setOnAction(e -> totpInput.requestFocus());
        totpInput.setOnAction(e -> userInput.requestFocus());
        userInput.setOnAction(e -> passwordInput.requestFocus());
        passwordInput.setOnAction(e -> addBtn.fire());
        separator = new Region();
        separator.setPrefHeight(1);

        ScrollPane scrollPane = new ScrollPane(entriesContainer);
        entriesScrollPane = scrollPane;
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-control-inner-background: transparent; -fx-vbar-policy: as-needed; -fx-hbar-policy: never;");

        centerContainer.getChildren().addAll(topHeader, inputForm, separator, scrollPane);
        mainLayout.setCenter(centerContainer);

        setupButtonHoverListeners();
        loadUsersFromDatabase();
        applyTheme();

        Scene mainScene = new Scene(mainLayout, 1280, 720);
        mainScene.addEventFilter(MouseEvent.ANY, e -> lastActivityMillis = System.currentTimeMillis());
        mainScene.addEventFilter(KeyEvent.ANY, e -> lastActivityMillis = System.currentTimeMillis());
        primaryStage.setScene(mainScene);
        primaryStage.show();
        primaryStage.toFront();
        Platform.runLater(() -> {
            styleScrollbars(entriesScrollPane);
            styleScrollbars(sidebarScrollPane);
        });
        startAutoLockTimer();
    }

    private void styleScrollbars(ScrollPane sp) {
        if (sp == null) return;
        sp.applyCss();
        sp.layout();
        for (Node n : sp.lookupAll(".scroll-bar")) {
            n.setStyle("-fx-background-color: transparent;");
            if (n instanceof ScrollBar bar) {
                bar.setPrefSize(8, 8);
            }
        }
        for (Node n : sp.lookupAll(".increment-button")) n.setVisible(false);
        for (Node n : sp.lookupAll(".decrement-button")) n.setVisible(false);
        for (Node n : sp.lookupAll(".increment-arrow")) n.setVisible(false);
        for (Node n : sp.lookupAll(".decrement-arrow")) n.setVisible(false);
        for (Node n : sp.lookupAll(".track")) {
            n.setStyle("-fx-background-color: transparent;");
        }
        String thumbColor = isDarkMode ? "#3f3f46" : "#bfc0c6";
        for (Node n : sp.lookupAll(".thumb")) {
            n.setStyle("-fx-background-color: " + thumbColor + "; -fx-background-radius: 4px; -fx-background-insets: 2 3 2 3;");
        }
    }

    private void applyTheme() {
        if (isDarkMode) {
            mainLayout.setStyle("-fx-background-color: #09090b;");
            sidebar.setStyle("-fx-background-color: #121214; -fx-border-color: #27272a; -fx-border-width: 0 1px 0 0;");
            menuTitle.setStyle("-fx-text-fill: #71717a; -fx-font-weight: bold; -fx-font-size: 11px; -fx-letter-spacing: 1.5px;");
            titleLabel.setStyle("-fx-font-size: 26px; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', sans-serif;");
            inputForm.setStyle("-fx-background-color: #18181b; -fx-background-radius: 16px; -fx-border-color: #27272a; -fx-border-radius: 16px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 15, 0, 0, 8);");
            separator.setStyle("-fx-background-color: #27272a;");
            themeToggleBtn.setText("☀");
        } else {
            mainLayout.setStyle("-fx-background-color: #f4f4f5;");
            sidebar.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e4e4e7; -fx-border-width: 0 1px 0 0;");
            menuTitle.setStyle("-fx-text-fill: #a1a1aa; -fx-font-weight: bold; -fx-font-size: 11px; -fx-letter-spacing: 1.5px;");
            titleLabel.setStyle("-fx-font-size: 26px; -fx-text-fill: #09090b; -fx-font-weight: bold; -fx-font-family: 'Segoe UI', sans-serif;");
            inputForm.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 16px; -fx-border-color: #e4e4e7; -fx-border-radius: 16px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.06), 20, 0, 0, 10);");
            separator.setStyle("-fx-background-color: #e4e4e7;");
            themeToggleBtn.setText("🌙");
        }

        applyButtonStyle(toggleSidebarBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #a1a1aa; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 50em;",
                "-fx-background-color: #27272a; -fx-text-fill: #ffffff; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 50em;",
                "-fx-background-color: #ffffff; -fx-text-fill: #71717a; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 50em;",
                "-fx-background-color: #e4e4e7; -fx-text-fill: #09090b; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #d4d4d8; -fx-border-radius: 50em;"
        );

        applyButtonStyle(themeToggleBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #fbbf24; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 50em;",
                "-fx-background-color: #27272a; -fx-text-fill: #fbbf24; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 50em;",
                "-fx-background-color: #ffffff; -fx-text-fill: #4b5563; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 50em;",
                "-fx-background-color: #e4e4e7; -fx-text-fill: #4b5563; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #d4d4d8; -fx-border-radius: 50em;"
        );

        applyButtonStyle(logoutBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #f87171; -fx-font-size: 16px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 50em;",
                "-fx-background-color: #27272a; -fx-text-fill: #ef4444; -fx-font-size: 16px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 50em;",
                "-fx-background-color: #ffffff; -fx-text-fill: #dc2626; -fx-font-size: 16px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 50em;",
                "-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-size: 16px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #fecaca; -fx-border-radius: 50em;"
        );

        String langText = I18n.isEnglish() ? "DE" : "EN";
        applyButtonStyle(langBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #38bdf8; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 50em;",
                "-fx-background-color: #27272a; -fx-text-fill: #7dd3fc; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 50em;",
                "-fx-background-color: #ffffff; -fx-text-fill: #0284c7; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 50em;",
                "-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #bae6fd; -fx-border-radius: 50em;"
        );
        if (!langText.equals(langBtn.getText())) langBtn.setText(langText);

        applyButtonStyle(generateBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #a1a1aa; -fx-font-size: 20px; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 12px;",
                "-fx-background-color: #27272a; -fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 12px;",
                "-fx-background-color: #ffffff; -fx-text-fill: #71717a; -fx-font-size: 20px; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 12px;",
                "-fx-background-color: #f4f4f5; -fx-text-fill: #09090b; -fx-font-size: 20px; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #d4d4d8; -fx-border-radius: 12px;"
        );

        applyButtonStyle(addUserBtn,
                "-fx-background-color: transparent; -fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 18px; -fx-padding: 0; -fx-cursor: hand;",
                "-fx-background-color: transparent; -fx-text-fill: #34d399; -fx-font-weight: bold; -fx-font-size: 18px; -fx-padding: 0; -fx-cursor: hand;",
                "-fx-background-color: transparent; -fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 18px; -fx-padding: 0; -fx-cursor: hand;",
                "-fx-background-color: transparent; -fx-text-fill: #059669; -fx-font-weight: bold; -fx-font-size: 18px; -fx-padding: 0; -fx-cursor: hand;"
        );

        updateFormState();
        updateUserCardsVisualsOnly();
        updateEntryCardsVisualsOnly();
        Platform.runLater(() -> {
            styleScrollbars(entriesScrollPane);
            styleScrollbars(sidebarScrollPane);
        });
    }

    private void updateFormState() {
        boolean noUser = currentUser == null || currentUser.isEmpty();

        platformInput.setDisable(noUser);
        urlInput.setDisable(noUser);
        totpInput.setDisable(noUser);
        userInput.setDisable(noUser);
        passwordInput.setDisable(noUser);
        generateBtn.setDisable(noUser);

        updateTextFieldStyleImmediate(platformInput);
        updateTextFieldStyleImmediate(urlInput);
        updateTextFieldStyleImmediate(totpInput);
        updateTextFieldStyleImmediate(userInput);
        updateTextFieldStyleImmediate(passwordInput);

        if (noUser) {
            addBtn.setText(I18n.tr("👤 Bitte zuerst einen Workspace erstellen"));
            applyButtonStyle(addBtn,
                    "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;",
                    "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;",
                    "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;",
                    "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;"
            );
            setupIndividualButtonHover(addBtn,
                    "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;",
                    "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;",
                    "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;",
                    "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;"
            );
        } else {
            addBtn.setText(I18n.tr("＋ Eintrag sicher verschlüsseln"));
            applyButtonStyle(addBtn,
                    "-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;",
                    "-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;",
                    "-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;",
                    "-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;"
            );
            setupIndividualButtonHover(addBtn,
                    "-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;",
                    "-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;",
                    "-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;",
                    "-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 12px; -fx-cursor: hand; -fx-font-size: 14px;"
            );
        }
    }

    private void applyButtonStyle(Button btn, String darkNorm, String darkHover, String lightNorm, String lightHover) {
        if (btn == null) return;
        if (isDarkMode) btn.setStyle(btn.isHover() ? darkHover : darkNorm);
        else btn.setStyle(btn.isHover() ? lightHover : lightNorm);
    }

    private void setupButtonHoverListeners() {
        setupIndividualButtonHover(toggleSidebarBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #a1a1aa; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 50em;",
                "-fx-background-color: #27272a; -fx-text-fill: #ffffff; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 50em;",
                "-fx-background-color: #ffffff; -fx-text-fill: #71717a; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 50em;",
                "-fx-background-color: #e4e4e7; -fx-text-fill: #09090b; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #d4d4d8; -fx-border-radius: 50em;"
        );
        setupIndividualButtonHover(themeToggleBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #fbbf24; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 50em;",
                "-fx-background-color: #27272a; -fx-text-fill: #fbbf24; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 50em;",
                "-fx-background-color: #ffffff; -fx-text-fill: #4b5563; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 50em;",
                "-fx-background-color: #e4e4e7; -fx-text-fill: #4b5563; -fx-font-size: 15px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #d4d4d8; -fx-border-radius: 50em;"
        );
        setupIndividualButtonHover(logoutBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #f87171; -fx-font-size: 16px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 50em;",
                "-fx-background-color: #27272a; -fx-text-fill: #ef4444; -fx-font-size: 16px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 50em;",
                "-fx-background-color: #ffffff; -fx-text-fill: #dc2626; -fx-font-size: 16px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 50em;",
                "-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-size: 16px; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #fecaca; -fx-border-radius: 50em;"
        );
        setupIndividualButtonHover(langBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #38bdf8; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 50em;",
                "-fx-background-color: #27272a; -fx-text-fill: #7dd3fc; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 50em;",
                "-fx-background-color: #ffffff; -fx-text-fill: #0284c7; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 50em;",
                "-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 50em; -fx-cursor: hand; -fx-border-color: #bae6fd; -fx-border-radius: 50em;"
        );
        setupIndividualButtonHover(generateBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #a1a1aa; -fx-font-size: 20px; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 12px;",
                "-fx-background-color: #27272a; -fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 12px;",
                "-fx-background-color: #ffffff; -fx-text-fill: #71717a; -fx-font-size: 20px; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 12px;",
                "-fx-background-color: #f4f4f5; -fx-text-fill: #09090b; -fx-font-size: 20px; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #d4d4d8; -fx-border-radius: 12px;"
        );
        setupIndividualButtonHover(addUserBtn,
                "-fx-background-color: transparent; -fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 18px; -fx-padding: 0; -fx-cursor: hand;",
                "-fx-background-color: transparent; -fx-text-fill: #34d399; -fx-font-weight: bold; -fx-font-size: 18px; -fx-padding: 0; -fx-cursor: hand;",
                "-fx-background-color: transparent; -fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 18px; -fx-padding: 0; -fx-cursor: hand;",
                "-fx-background-color: transparent; -fx-text-fill: #059669; -fx-font-weight: bold; -fx-font-size: 18px; -fx-padding: 0; -fx-cursor: hand;"
        );
    }

    private void setupIndividualButtonHover(Button btn, String darkNorm, String darkHover, String lightNorm, String lightHover) {
        if (btn == null) return;
        btn.setOnMouseEntered(e -> btn.setStyle(isDarkMode ? darkHover : lightHover));
        btn.setOnMouseExited(e -> btn.setStyle(isDarkMode ? darkNorm : lightNorm));
    }

    private void updateTextFieldStyleImmediate(TextField tf) {
        if (tf == null) return;
        if (tf.isDisabled()) {
            String disabledStyle = isDarkMode ?
                    "-fx-background-color: #121214; -fx-text-fill: #52525b; -fx-prompt-text-fill: #3f3f46; -fx-border-color: #27272a; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 12px; -fx-font-size: 14px;" :
                    "-fx-background-color: #fafafa; -fx-text-fill: #a1a1aa; -fx-prompt-text-fill: #d4d4d8; -fx-border-color: #e4e4e7; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 12px; -fx-font-size: 14px;";
            tf.setStyle(disabledStyle);
            return;
        }
        String normalStyle = isDarkMode ?
                "-fx-background-color: #09090b; -fx-text-fill: #f4f4f5; -fx-prompt-text-fill: #52525b; -fx-border-color: #27272a; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 12px; -fx-font-size: 14px;" :
                "-fx-background-color: #f4f4f5; -fx-text-fill: #09090b; -fx-prompt-text-fill: #a1a1aa; -fx-border-color: #e4e4e7; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 12px; -fx-font-size: 14px;";
        String focusStyle = isDarkMode ?
                "-fx-background-color: #09090b; -fx-text-fill: #f4f4f5; -fx-prompt-text-fill: #52525b; -fx-border-color: #6366f1; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 12px; -fx-font-size: 14px;" :
                "-fx-background-color: #ffffff; -fx-text-fill: #09090b; -fx-prompt-text-fill: #a1a1aa; -fx-border-color: #4f46e5; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 12px; -fx-font-size: 14px; -fx-effect: dropshadow(three-pass-box, rgba(79, 70, 229, 0.1), 10, 0, 0, 0);";
        tf.setStyle(tf.isFocused() ? focusStyle : normalStyle);
    }

    private TextField createStyledTextField(String promptText) {
        TextField tf = new TextField();
        tf.setPromptText(promptText);
        tf.focusedProperty().addListener((obs, oldVal, newVal) -> updateTextFieldStyleImmediate(tf));
        updateTextFieldStyleImmediate(tf);
        return tf;
    }

    private VBox createAuthLayout(String title, String subtitle) {
        VBox center = new VBox(20);
        center.setAlignment(Pos.CENTER);
        center.setStyle("-fx-background-color: #09090b;");

        Button authLangBtn = new Button(I18n.isEnglish() ? "🌐 Deutsch" : "🌐 English");
        authLangBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #71717a; -fx-cursor: hand; -fx-font-size: 13px; -fx-padding: 6 12; -fx-background-radius: 50em; -fx-border-color: #27272a; -fx-border-radius: 50em;");
        authLangBtn.setOnMouseEntered(e -> authLangBtn.setStyle("-fx-background-color: #18181b; -fx-text-fill: #ffffff; -fx-cursor: hand; -fx-font-size: 13px; -fx-padding: 6 12; -fx-background-radius: 50em; -fx-border-color: #3f3f46; -fx-border-radius: 50em;"));
        authLangBtn.setOnMouseExited(e -> authLangBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #71717a; -fx-cursor: hand; -fx-font-size: 13px; -fx-padding: 6 12; -fx-background-radius: 50em; -fx-border-color: #27272a; -fx-border-radius: 50em;"));
        authLangBtn.setOnAction(e -> toggleLanguage());
        StackPane.setAlignment(authLangBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(authLangBtn, new Insets(18, 24, 0, 0));

        Label icon = new Label("🔒");
        icon.setStyle("-fx-font-size: 48px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: bold;");

        Label subLabel = new Label(subtitle);
        subLabel.setStyle("-fx-text-fill: #a1a1aa; -fx-font-size: 14px; -fx-text-alignment: center;");

        center.getChildren().addAll(icon, titleLabel, subLabel);

        StackPane root = new StackPane(center, authLangBtn);
        root.setStyle("-fx-background-color: #09090b;");
        center.setUserData(root);
        return center;
    }

    private void styleAuthField(TextField field) {
        field.setMaxWidth(350);
        field.setStyle("-fx-background-color: #18181b; -fx-text-fill: white; -fx-prompt-text-fill: #52525b; -fx-border-color: #27272a; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 15px; -fx-font-size: 16px;");
    }

    private Button createAuthButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(350);
        btn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-padding: 15px; -fx-cursor: hand; -fx-font-size: 16px;");
        return btn;
    }

    private void updateUserCardsVisualsOnly() {
        String activeBg = isDarkMode ? "#27272a" : "#e4e4e7";
        String inactiveBg = isDarkMode ? "#121214" : "#ffffff";
        String activeText = isDarkMode ? "#ffffff" : "#09090b";
        String inactiveText = isDarkMode ? "#a1a1aa" : "#71717a";

        for (Node node : usersContainer.getChildren()) {
            if (node instanceof StackPane) {
                StackPane card = (StackPane) node;
                HBox front = (HBox) card.getChildren().get(1);
                Label lbl = (Label) front.getChildren().get(0);
                String cardUser = (String) card.getUserData();

                if (cardUser != null && cardUser.equals(currentUser)) {
                    front.setStyle("-fx-background-color: " + activeBg + "; -fx-background-radius: 12px;");
                    lbl.setStyle("-fx-text-fill: " + activeText + "; -fx-font-weight: bold; -fx-font-size: 14px;");
                } else {
                    front.setStyle("-fx-background-color: " + inactiveBg + "; -fx-background-radius: 12px;");
                    lbl.setStyle("-fx-text-fill: " + inactiveText + "; -fx-font-weight: normal; -fx-font-size: 14px;");
                }
            }
        }
    }

    private void updateEntryCardsVisualsOnly() {
        String cardBg = isDarkMode ? "#18181b" : "#ffffff";
        String cardBorder = isDarkMode ? "#27272a" : "#e4e4e7";
        String titleText = isDarkMode ? "#ffffff" : "#09090b";
        String subtitleText = isDarkMode ? "#a1a1aa" : "#71717a";

        String btnBgNormal = isDarkMode ? "#4f46e5" : "#6366f1";
        String utilityBtnBg = isDarkMode ? "#27272a" : "#e4e4e7";
        String utilityBtnText = isDarkMode ? "#ffffff" : "#09090b";

        for (Node node : entriesContainer.getChildren()) {
            if (node instanceof StackPane) {
                StackPane card = (StackPane) node;
                HBox frontLayer = (HBox) card.getChildren().get(1);
                frontLayer.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 14px; -fx-border-color: " + cardBorder + "; -fx-border-radius: 14px;");

                VBox textContainer = (VBox) frontLayer.getChildren().get(0);
                textContainer.getChildren().get(0).setStyle("-fx-text-fill: " + titleText + "; -fx-font-weight: bold; -fx-font-size: 16px;");
                textContainer.getChildren().get(1).setStyle("-fx-text-fill: " + subtitleText + "; -fx-font-size: 13px;");

                HBox passBox = (HBox) textContainer.getChildren().get(2);
                Label passLabel = (Label) passBox.getChildren().get(0);
                passLabel.setStyle("-fx-text-fill: " + subtitleText + "; -fx-font-size: 14px;");

                Button eyeBtn = (Button) passBox.getChildren().get(1);
                eyeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + subtitleText + "; -fx-cursor: hand; -fx-font-size: 13px;");

                if (textContainer.getChildren().size() > 3 && textContainer.getChildren().get(3) instanceof HBox totpRow) {
                    for (Node rowNode : totpRow.getChildren()) {
                        if (rowNode instanceof Label lbl) {
                            if ("totpCode".equals(lbl.getId())) {
                                lbl.setStyle("-fx-text-fill: " + titleText + "; -fx-font-weight: bold; -fx-font-size: 15px; -fx-font-family: 'Consolas', 'Courier New', monospace; -fx-letter-spacing: 2px;");
                            } else {
                                lbl.setStyle("-fx-text-fill: " + subtitleText + "; -fx-font-size: " + (lbl.getText().endsWith("s") ? "12px;" : "13px;"));
                            }
                        } else if (rowNode instanceof ProgressBar pb) {
                            pb.setStyle("-fx-accent: " + btnBgNormal + ";");
                        } else if (rowNode instanceof Button b) {
                            b.setStyle("-fx-background-color: transparent; -fx-text-fill: " + subtitleText + "; -fx-cursor: hand; -fx-font-size: 13px;");
                        }
                    }
                }

                HBox buttonBox = (HBox) frontLayer.getChildren().get(2);
                for (Node btnNode : buttonBox.getChildren()) {
                    if (btnNode instanceof Button) {
                        Button btn = (Button) btnNode;
                        if ("copy".equals(btn.getUserData())) {
                            btn.setStyle("-fx-background-color: " + btnBgNormal + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand; -fx-font-size: 13px;");
                        } else {
                            btn.setStyle("-fx-background-color: " + utilityBtnBg + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;");
                        }
                    }
                }
            }
        }
    }

    private void copyToClipboard(String text) {
        if(text == null) return;
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);

        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(15), evt -> {
            if (clipboard.hasString() && clipboard.getString().equals(text)) {
                clipboard.clear();
            }
        }));
        timeline.setCycleCount(1);
        timeline.play();
    }

    private void showPasswordGeneratorDialog(TextField targetField) {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(primaryStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(15);
        root.setPadding(new Insets(24));
        root.setPrefWidth(380);

        String dropShadow = isDarkMode ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.15)";
        root.setStyle(isDarkMode ?
                "-fx-background-color: #18181b; -fx-border-color: #3f3f46; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);" :
                "-fx-background-color: #ffffff; -fx-border-color: #e4e4e7; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);");

        Label title = new Label(I18n.tr("Passwort generieren"));
        title.setStyle(isDarkMode ? "-fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-font-weight: bold;" : "-fx-text-fill: #09090b; -fx-font-size: 20px; -fx-font-weight: bold;");

        TextField previewField = new TextField();
        previewField.setEditable(false);
        updateTextFieldStyleImmediate(previewField);

        Button refreshBtn = new Button(I18n.tr("🔄 Neu würfeln"));
        refreshBtn.setStyle(isDarkMode ?
                "-fx-background-color: #27272a; -fx-text-fill: #ffffff; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 12px; -fx-font-size: 13px;" :
                "-fx-background-color: #e4e4e7; -fx-text-fill: #09090b; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 12px; -fx-font-size: 13px;");

        HBox previewBox = new HBox(10);
        HBox.setHgrow(previewField, Priority.ALWAYS);
        previewBox.getChildren().addAll(previewField, refreshBtn);

        HBox lengthBox = new HBox(10);
        lengthBox.setAlignment(Pos.CENTER_LEFT);
        Label lengthLabel = new Label(I18n.tr("Länge: {0}", 16));
        lengthLabel.setPrefWidth(70);
        lengthLabel.setStyle(isDarkMode ? "-fx-text-fill: #a1a1aa; -fx-font-weight: bold;" : "-fx-text-fill: #71717a; -fx-font-weight: bold;");

        Slider lengthSlider = new Slider(8, 64, 16);
        lengthSlider.setBlockIncrement(1);
        HBox.setHgrow(lengthSlider, Priority.ALWAYS);
        lengthBox.getChildren().addAll(lengthLabel, lengthSlider);

        CheckBox cbUpper = new CheckBox(I18n.tr("Großbuchstaben (A-Z)"));
        CheckBox cbLower = new CheckBox(I18n.tr("Kleinbuchstaben (a-z)"));
        CheckBox cbNumbers = new CheckBox(I18n.tr("Zahlen (0-9)"));
        CheckBox cbSpecial = new CheckBox(I18n.tr("Sonderzeichen (!@#...)"));

        String cbStyle = isDarkMode ? "-fx-text-fill: #e4e4e7; -fx-font-size: 14px;" : "-fx-text-fill: #18181b; -fx-font-size: 14px;";
        cbUpper.setStyle(cbStyle); cbLower.setStyle(cbStyle); cbNumbers.setStyle(cbStyle); cbSpecial.setStyle(cbStyle);

        cbUpper.setSelected(true); cbLower.setSelected(true); cbNumbers.setSelected(true); cbSpecial.setSelected(true);

        VBox optionsBox = new VBox(10);
        optionsBox.setPadding(new Insets(10, 0, 10, 0));
        optionsBox.getChildren().addAll(cbUpper, cbLower, cbNumbers, cbSpecial);

        Runnable updatePreview = () -> {
            int len = (int) lengthSlider.getValue();
            lengthLabel.setText(I18n.tr("Länge: {0}", len));
            if (!cbUpper.isSelected() && !cbLower.isSelected() && !cbNumbers.isSelected() && !cbSpecial.isSelected()) {
                cbLower.setSelected(true);
            }
            String newPassword = generateRandomPassword(len, cbUpper.isSelected(), cbLower.isSelected(), cbNumbers.isSelected(), cbSpecial.isSelected());
            previewField.setText(newPassword);
        };

        lengthSlider.valueProperty().addListener((obs, oldV, newV) -> updatePreview.run());
        cbUpper.setOnAction(e -> updatePreview.run());
        cbLower.setOnAction(e -> updatePreview.run());
        cbNumbers.setOnAction(e -> updatePreview.run());
        cbSpecial.setOnAction(e -> updatePreview.run());
        refreshBtn.setOnAction(e -> updatePreview.run());

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        Button cancelBtn = new Button(I18n.tr("Abbrechen"));
        Button applyBtn = new Button(I18n.tr("Übernehmen"));

        String cancelBtnNormal = isDarkMode ? "-fx-background-color: transparent; -fx-text-fill: #a1a1aa; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8px 16px;" : "-fx-background-color: transparent; -fx-text-fill: #71717a; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8px 16px;";
        String cancelBtnHover = isDarkMode ? "-fx-background-color: #27272a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;" : "-fx-background-color: #f4f4f5; -fx-text-fill: #09090b; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;";

        String applyBtnNormal = "-fx-background-color: #4f46e5; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;";
        String applyBtnHover = "-fx-background-color: #4338ca; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;";

        cancelBtn.setStyle(cancelBtnNormal);
        cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle(cancelBtnHover));
        cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle(cancelBtnNormal));
        cancelBtn.setOnAction(e -> dialogStage.close());

        applyBtn.setStyle(applyBtnNormal);
        applyBtn.setOnMouseEntered(e -> applyBtn.setStyle(applyBtnHover));
        applyBtn.setOnMouseExited(e -> applyBtn.setStyle(applyBtnNormal));
        applyBtn.setOnAction(e -> {
            if (targetField != null) targetField.setText(previewField.getText());
            dialogStage.close();
        });

        buttonBox.getChildren().addAll(cancelBtn, applyBtn);
        updatePreview.run();

        root.getChildren().addAll(title, previewBox, lengthBox, optionsBox, buttonBox);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }

    private String generateRandomPassword(int length, boolean useUpper, boolean useLower, boolean useNumbers, boolean useSpecial) {
        String upperChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerChars = "abcdefghijklmnopqrstuvwxyz";
        String numberChars = "0123456789";
        String specialChars = "!@#$%^&*()-_=+[]{};:,.<>/?";

        StringBuilder pool = new StringBuilder();
        if (useUpper) pool.append(upperChars);
        if (useLower) pool.append(lowerChars);
        if (useNumbers) pool.append(numberChars);
        if (useSpecial) pool.append(specialChars);

        if (pool.length() == 0) return "";

        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(length);

        if (useUpper) password.append(upperChars.charAt(random.nextInt(upperChars.length())));
        if (useLower) password.append(lowerChars.charAt(random.nextInt(lowerChars.length())));
        if (useNumbers) password.append(numberChars.charAt(random.nextInt(numberChars.length())));
        if (useSpecial) password.append(specialChars.charAt(random.nextInt(specialChars.length())));

        while (password.length() < length) {
            password.append(pool.charAt(random.nextInt(pool.length())));
        }

        char[] passChars = password.toString().toCharArray();
        for (int i = 0; i < passChars.length; i++) {
            int randomIndex = random.nextInt(passChars.length);
            char temp = passChars[i];
            passChars[i] = passChars[randomIndex];
            passChars[randomIndex] = temp;
        }

        return new String(passChars);
    }

    private void showAddUserDialog() {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(primaryStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setPrefWidth(350);

        String dropShadow = isDarkMode ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.15)";
        root.setStyle(isDarkMode ?
                "-fx-background-color: #18181b; -fx-border-color: #3f3f46; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);" :
                "-fx-background-color: #ffffff; -fx-border-color: #e4e4e7; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);");

        Label title = new Label(I18n.tr("Neuer Workspace"));
        title.setStyle(isDarkMode ? "-fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-font-weight: bold;" : "-fx-text-fill: #09090b; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label subtitle = new Label(I18n.tr("Gib einen Namen für das neue Konto ein."));
        subtitle.setStyle(isDarkMode ? "-fx-text-fill: #a1a1aa; -fx-font-size: 13px;" : "-fx-text-fill: #71717a; -fx-font-size: 13px;");

        TextField nameInput = new TextField();
        nameInput.setPromptText(I18n.tr("z.B. Arbeit, Privat..."));
        updateTextFieldStyleImmediate(nameInput);
        nameInput.focusedProperty().addListener((obs, oldVal, newVal) -> updateTextFieldStyleImmediate(nameInput));

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        Button cancelBtn = new Button(I18n.tr("Abbrechen"));
        Button createBtn = new Button(I18n.tr("Erstellen"));

        String cancelBtnNormal = isDarkMode ? "-fx-background-color: transparent; -fx-text-fill: #a1a1aa; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8px 16px;" : "-fx-background-color: transparent; -fx-text-fill: #71717a; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8px 16px;";
        String cancelBtnHover = isDarkMode ? "-fx-background-color: #27272a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;" : "-fx-background-color: #f4f4f5; -fx-text-fill: #09090b; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;";
        String createBtnNormal = "-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;";
        String createBtnHover = "-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;";
        String createBtnDisabled = isDarkMode ? "-fx-background-color: #27272a; -fx-text-fill: #52525b; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px;" : "-fx-background-color: #e4e4e7; -fx-text-fill: #a1a1aa; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px;";

        cancelBtn.setStyle(cancelBtnNormal);
        cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle(cancelBtnHover));
        cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle(cancelBtnNormal));
        createBtn.setStyle(createBtnDisabled);
        createBtn.setDisable(true);

        nameInput.textProperty().addListener((obs, old, newVal) -> {
            boolean empty = newVal == null || newVal.trim().isEmpty();
            createBtn.setDisable(empty);
            createBtn.setStyle(empty ? createBtnDisabled : createBtnNormal);
        });

        createBtn.setOnMouseEntered(e -> { if (!createBtn.isDisabled()) createBtn.setStyle(createBtnHover); });
        createBtn.setOnMouseExited(e -> { if (!createBtn.isDisabled()) createBtn.setStyle(createBtnNormal); });
        cancelBtn.setOnAction(e -> dialogStage.close());

        Runnable performCreate = () -> {
            String trimmedName = nameInput.getText() != null ? nameInput.getText().trim() : "";
            if (!trimmedName.isEmpty()) {
                if (saveUserToDatabase(trimmedName)) {
                    loadUsersFromDatabase();
                    switchUser(trimmedName);
                    dialogStage.close();
                } else {
                    showAlert(I18n.tr("Dieses Konto existiert bereits!"));
                }
            }
        };

        createBtn.setOnAction(e -> performCreate.run());
        nameInput.setOnAction(e -> { if (!createBtn.isDisabled()) performCreate.run(); });

        buttonBox.getChildren().addAll(cancelBtn, createBtn);
        root.getChildren().addAll(title, subtitle, nameInput, buttonBox);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialogStage.setScene(scene);

        Platform.runLater(nameInput::requestFocus);
        dialogStage.showAndWait();
    }

    private void showPasswordPromptForEdit(Runnable onSuccess) {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(primaryStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setPrefWidth(350);

        String dropShadow = isDarkMode ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.15)";
        root.setStyle(isDarkMode ?
                "-fx-background-color: #18181b; -fx-border-color: #3f3f46; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);" :
                "-fx-background-color: #ffffff; -fx-border-color: #e4e4e7; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);");

        Label title = new Label(I18n.tr("Sicherheitsprüfung"));
        title.setStyle(isDarkMode ? "-fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-font-weight: bold;" : "-fx-text-fill: #09090b; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label subtitle = new Label(I18n.tr("Bitte Master-Passwort eingeben:"));
        subtitle.setStyle(isDarkMode ? "-fx-text-fill: #a1a1aa; -fx-font-size: 13px;" : "-fx-text-fill: #71717a; -fx-font-size: 13px;");

        PasswordField passInput = new PasswordField();
        passInput.setPromptText(I18n.tr("Master-Passwort"));
        updateTextFieldStyleImmediate(passInput);
        passInput.focusedProperty().addListener((obs, oldVal, newVal) -> updateTextFieldStyleImmediate(passInput));

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button(I18n.tr("Abbrechen"));
        Button confirmBtn = new Button(I18n.tr("Bestätigen"));

        String cancelBtnNormal = isDarkMode ? "-fx-background-color: transparent; -fx-text-fill: #a1a1aa; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8px 16px;" : "-fx-background-color: transparent; -fx-text-fill: #71717a; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8px 16px;";
        String cancelBtnHover = isDarkMode ? "-fx-background-color: #27272a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;" : "-fx-background-color: #f4f4f5; -fx-text-fill: #09090b; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;";
        String confirmBtnNormal = "-fx-background-color: #4f46e5; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;";
        String confirmBtnHover = "-fx-background-color: #4338ca; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;";

        cancelBtn.setStyle(cancelBtnNormal);
        cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle(cancelBtnHover));
        cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle(cancelBtnNormal));
        cancelBtn.setOnAction(e -> dialogStage.close());

        confirmBtn.setStyle(confirmBtnNormal);
        confirmBtn.setOnMouseEntered(e -> confirmBtn.setStyle(confirmBtnHover));
        confirmBtn.setOnMouseExited(e -> confirmBtn.setStyle(confirmBtnNormal));

        Runnable executeConfirm = () -> {
            if (tryUnlockWithPassword(passInput.getText())) {
                dialogStage.close();
                Platform.runLater(onSuccess);
            } else {
                passInput.setStyle("-fx-border-color: #ef4444; -fx-background-color: " + (isDarkMode ? "#121214" : "#fafafa") + "; -fx-text-fill: " + (isDarkMode ? "white" : "black") + "; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 12px;");
            }
        };

        confirmBtn.setOnAction(e -> executeConfirm.run());
        passInput.setOnAction(e -> executeConfirm.run());

        buttonBox.getChildren().addAll(cancelBtn, confirmBtn);
        root.getChildren().addAll(title, subtitle, passInput, buttonBox);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialogStage.setScene(scene);

        Platform.runLater(passInput::requestFocus);
        dialogStage.showAndWait();
    }

    private void showEditDialog(int id, String oldPlat, String oldUrl, String oldUser, String oldPass, String oldTotp) {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(primaryStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(0);
        root.setPrefWidth(420);
        root.setMinHeight(Region.USE_PREF_SIZE);

        String dropShadow = isDarkMode ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.15)";
        root.setStyle(isDarkMode ?
                "-fx-background-color: #18181b; -fx-border-color: #3f3f46; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);" :
                "-fx-background-color: #ffffff; -fx-border-color: #e4e4e7; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);");

        VBox header = new VBox(5);
        header.setPadding(new Insets(24, 24, 16, 24));
        Label title = new Label(I18n.tr("Eintrag bearbeiten"));
        title.setStyle(isDarkMode ? "-fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-font-weight: bold;" : "-fx-text-fill: #09090b; -fx-font-size: 20px; -fx-font-weight: bold;");
        Label subTitle = new Label(I18n.tr("Passe die Felder an und speichere die Änderungen."));
        subTitle.setStyle(isDarkMode ? "-fx-text-fill: #a1a1aa; -fx-font-size: 13px;" : "-fx-text-fill: #71717a; -fx-font-size: 13px;");
        header.getChildren().addAll(title, subTitle);

        VBox formBox = new VBox(16);
        formBox.setPadding(new Insets(0, 24, 16, 24));

        TextField platField = createStyledTextField(I18n.tr("Plattform"));
        platField.setText(oldPlat);

        TextField urlField = createStyledTextField(I18n.tr("URL (Optional)"));
        urlField.setText(oldUrl);

        TextField totpField = createStyledTextField(I18n.tr("2FA-Schlüssel (optional, für Login-Codes)"));
        totpField.setText(oldTotp);
        Button totpFieldHelpBtn = new Button("?");
        totpFieldHelpBtn.setPrefSize(46, 46);
        totpFieldHelpBtn.setMinSize(46, 46);
        applyButtonStyle(totpFieldHelpBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #38bdf8; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 12px;",
                "-fx-background-color: #27272a; -fx-text-fill: #7dd3fc; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 12px;",
                "-fx-background-color: #ffffff; -fx-text-fill: #0284c7; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 12px;",
                "-fx-background-color: #f4f4f5; -fx-text-fill: #0369a1; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #d4d4d8; -fx-border-radius: 12px;"
        );
        setupIndividualButtonHover(totpFieldHelpBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #38bdf8; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 12px;",
                "-fx-background-color: #27272a; -fx-text-fill: #7dd3fc; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 12px;",
                "-fx-background-color: #ffffff; -fx-text-fill: #0284c7; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 12px;",
                "-fx-background-color: #f4f4f5; -fx-text-fill: #0369a1; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #d4d4d8; -fx-border-radius: 12px;"
        );
        totpFieldHelpBtn.setOnAction(e -> showTotpHelpDialog());
        HBox totpFieldBox = new HBox(10);
        HBox.setHgrow(totpField, Priority.ALWAYS);
        totpFieldBox.getChildren().addAll(totpField, totpFieldHelpBtn);

        TextField userField = createStyledTextField(I18n.tr("Benutzername"));
        userField.setText(oldUser);

        HBox passBox = new HBox(10);
        TextField passField = createStyledTextField(I18n.tr("Passwort"));
        passField.setText(oldPass);
        HBox.setHgrow(passField, Priority.ALWAYS);

        Button genBtn = new Button("🎲");
        genBtn.setPrefSize(46, 46);
        genBtn.setMinSize(46, 46);
        applyButtonStyle(genBtn,
                "-fx-background-color: #18181b; -fx-text-fill: #a1a1aa; -fx-font-size: 20px; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #27272a; -fx-border-radius: 12px;",
                "-fx-background-color: #27272a; -fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #3f3f46; -fx-border-radius: 12px;",
                "-fx-background-color: #ffffff; -fx-text-fill: #71717a; -fx-font-size: 20px; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #e4e4e7; -fx-border-radius: 12px;",
                "-fx-background-color: #f4f4f5; -fx-text-fill: #09090b; -fx-font-size: 20px; -fx-background-radius: 12px; -fx-cursor: hand; -fx-border-color: #d4d4d8; -fx-border-radius: 12px;"
        );
        genBtn.setOnAction(e -> showPasswordGeneratorDialog(passField));
        passBox.getChildren().addAll(passField, genBtn);

        formBox.getChildren().addAll(platField, urlField, totpFieldBox, userField, passBox);

        platField.setOnAction(e -> urlField.requestFocus());
        urlField.setOnAction(e -> totpField.requestFocus());
        totpField.setOnAction(e -> userField.requestFocus());
        userField.setOnAction(e -> passField.requestFocus());

        ScrollPane scrollPane = new ScrollPane(formBox);
        scrollPane.setFitToWidth(true);

        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-control-inner-background: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(16, 24, 24, 24));

        Button cancelBtn = new Button(I18n.tr("Abbrechen"));
        Button saveBtn = new Button(I18n.tr("Speichern"));

        String cancelBtnNormal = isDarkMode ? "-fx-background-color: transparent; -fx-text-fill: #a1a1aa; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8px 16px;" : "-fx-background-color: transparent; -fx-text-fill: #71717a; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8px 16px;";
        String cancelBtnHover = isDarkMode ? "-fx-background-color: #27272a; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;" : "-fx-background-color: #f4f4f5; -fx-text-fill: #09090b; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;";
        String saveBtnNormal = "-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;";
        String saveBtnHover = "-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;";

        cancelBtn.setStyle(cancelBtnNormal);
        cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle(cancelBtnHover));
        cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle(cancelBtnNormal));
        cancelBtn.setOnAction(e -> dialogStage.close());

        saveBtn.setStyle(saveBtnNormal);
        saveBtn.setOnMouseEntered(e -> saveBtn.setStyle(saveBtnHover));
        saveBtn.setOnMouseExited(e -> saveBtn.setStyle(saveBtnNormal));
        saveBtn.setOnAction(e -> {
            String p = platField.getText() != null ? platField.getText().trim() : "";
            String u = urlField.getText() != null ? urlField.getText().trim() : "";
            String tf = TotpUtil.normalize(totpField.getText());

            if (!tf.isEmpty() && !TotpUtil.isPlausible(tf)) {
                showAlert(I18n.tr("Das 2FA-Secret ist ungültig.\nErlaubt ist ein Base32-Secret oder ein otpauth://-Link."));
                totpField.requestFocus();
                return;
            }

            String n = userField.getText() != null ? userField.getText().trim() : "";
            String pw = passField.getText() != null ? passField.getText().trim() : "";

            if (!p.isEmpty() && !n.isEmpty() && !pw.isEmpty()) {
                updateInDatabase(id, p, u, n, pw, tf);
                stopAllTotpTimelines();
                entriesContainer.getChildren().clear();
                loadEntriesFromDatabase();
                dialogStage.close();
            } else {
                showAlert(I18n.tr("Plattform, Benutzername und Passwort dürfen nicht leer sein."));
            }
        });

        buttonBox.getChildren().addAll(cancelBtn, saveBtn);
        root.getChildren().addAll(header, scrollPane, buttonBox);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialogStage.setScene(scene);

        Platform.runLater(() -> styleScrollbars(scrollPane));
        Platform.runLater(platField::requestFocus);
        dialogStage.showAndWait();
    }

    private boolean showConfirmDialog(String title, String message, String confirmText) {
        final boolean[] confirmed = {false};
        Stage dialogStage = new Stage();
        dialogStage.initOwner(primaryStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(15);
        root.setPadding(new Insets(24));
        root.setPrefWidth(360);

        String dropShadow = isDarkMode ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.15)";
        String mainText = isDarkMode ? "#ffffff" : "#09090b";
        String subText = isDarkMode ? "#a1a1aa" : "#71717a";
        String borderColor = isDarkMode ? "#fbbf24" : "#d97706";
        root.setStyle("-fx-background-color: " + (isDarkMode ? "#18181b" : "#ffffff") + "; -fx-border-color: " + borderColor + "; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("⚠️");
        icon.setStyle("-fx-font-size: 20px;");
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: " + mainText + "; -fx-font-size: 18px; -fx-font-weight: bold;");
        header.getChildren().addAll(icon, titleLabel);

        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.setStyle("-fx-text-fill: " + subText + "; -fx-font-size: 14px;");

        Button confirmBtn = new Button(confirmText);
        confirmBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;");
        confirmBtn.setOnMouseEntered(e -> confirmBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;"));
        confirmBtn.setOnMouseExited(e -> confirmBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;"));
        confirmBtn.setOnAction(e -> {
            confirmed[0] = true;
            dialogStage.close();
        });

        Button cancelBtn = new Button(I18n.tr("Abbrechen"));
        String cancelNormal = "-fx-background-color: transparent; -fx-text-fill: " + subText + "; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8px 16px;";
        String cancelHover = "-fx-background-color: " + (isDarkMode ? "#27272a" : "#e4e4e7") + "; -fx-text-fill: " + mainText + "; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px;";
        cancelBtn.setStyle(cancelNormal);
        cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle(cancelHover));
        cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle(cancelNormal));
        cancelBtn.setOnAction(e -> dialogStage.close());

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        btnBox.getChildren().addAll(cancelBtn, confirmBtn);

        root.getChildren().addAll(header, msgLabel, btnBox);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialogStage.setScene(scene);
        Platform.runLater(confirmBtn::requestFocus);
        dialogStage.showAndWait();
        return confirmed[0];
    }

    private void refreshEmptyEntriesHint() {
        entriesContainer.getChildren().removeIf(n -> n instanceof Label l && "emptyHint".equals(l.getId()));
        if (currentUser == null || currentUser.isEmpty()) return;
        boolean hasEntries = false;
        for (Node n : entriesContainer.getChildren()) {
            if (n instanceof StackPane) {
                hasEntries = true;
                break;
            }
        }
        if (hasEntries) return;
        Label hint = new Label(I18n.tr("Noch keine Einträge für \"{0}\".\nFüge oben deinen ersten Eintrag hinzu.", currentUser));
        hint.setId("emptyHint");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: " + (isDarkMode ? "#52525b" : "#a1a1aa") + "; -fx-font-size: 14px; -fx-text-alignment: center; -fx-padding: 30px;");
        entriesContainer.getChildren().add(hint);
    }

    private void showTotpHelpDialog() {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(primaryStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(16);
        root.setPadding(new Insets(26));
        root.setPrefWidth(430);

        String dropShadow = isDarkMode ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.15)";
        String mainText = isDarkMode ? "#ffffff" : "#09090b";
        String subText = isDarkMode ? "#a1a1aa" : "#71717a";
        root.setStyle("-fx-background-color: " + (isDarkMode ? "#18181b" : "#ffffff") + "; -fx-border-color: #10b981; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);");

        Label title = new Label("🔐 " + I18n.tr("Wie funktioniert 2FA?"));
        title.setStyle("-fx-text-fill: " + mainText + "; -fx-font-size: 19px; -fx-font-weight: bold;");
        title.setWrapText(true);

        Label intro = new Label(I18n.tr("2FA schützt deine Konten mit einem zusätzlichen Code: einer 6-stelligen Zahl, die sich alle 30 Sekunden ändert."));
        intro.setWrapText(true);
        intro.setStyle("-fx-text-fill: " + subText + "; -fx-font-size: 14px;");

        Label steps = new Label(I18n.tr("So einfach gehts:\n1. Öffne die Website → Sicherheit → Zwei-Faktor-Aktivierung.\n2. Wähle \"Authenticator-App\", dann \"Kann nicht scannen?\" oder \"Schlüssel anzeigen\".\n3. Kopiere den angezeigten Schlüssel in das Feld hier.\n\nBeim Login fragt dich die Website dann nach dem aktuellen Code aus dieser App."));
        steps.setWrapText(true);
        steps.setStyle("-fx-text-fill: " + subText + "; -fx-font-size: 14px;");

        Button okBtn = new Button(I18n.tr("Alles klar!"));
        okBtn.setMaxWidth(Double.MAX_VALUE);
        okBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 12px; -fx-font-size: 14px;");
        okBtn.setOnMouseEntered(e -> okBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 12px; -fx-font-size: 14px;"));
        okBtn.setOnMouseExited(e -> okBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 12px; -fx-font-size: 14px;"));
        okBtn.setOnAction(e -> dialogStage.close());

        root.getChildren().addAll(title, intro, steps, okBtn);
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }

    private void showAlert(String message) {
        Stage alertStage = new Stage();
        alertStage.initOwner(primaryStage);
        alertStage.initModality(Modality.APPLICATION_MODAL);
        alertStage.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(15);
        root.setPadding(new Insets(24));
        root.setPrefWidth(320);

        String dropShadow = isDarkMode ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.15)";
        root.setStyle(isDarkMode ?
                "-fx-background-color: #18181b; -fx-border-color: #ef4444; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);" :
                "-fx-background-color: #ffffff; -fx-border-color: #ef4444; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("⚠️");
        icon.setStyle("-fx-font-size: 20px;");
        Label title = new Label(I18n.tr("Hinweis"));
        title.setStyle(isDarkMode ? "-fx-text-fill: #ffffff; -fx-font-size: 18px; -fx-font-weight: bold;" : "-fx-text-fill: #09090b; -fx-font-size: 18px; -fx-font-weight: bold;");
        header.getChildren().addAll(icon, title);

        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.setStyle(isDarkMode ? "-fx-text-fill: #a1a1aa; -fx-font-size: 14px;" : "-fx-text-fill: #71717a; -fx-font-size: 14px;");

        Button okBtn = new Button(I18n.tr("Verstanden"));
        String okBtnNormal = "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px; -fx-min-width: 100px;";
        String okBtnHover = "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 16px; -fx-min-width: 100px;";
        okBtn.setStyle(okBtnNormal);
        okBtn.setOnMouseEntered(e -> okBtn.setStyle(okBtnHover));
        okBtn.setOnMouseExited(e -> okBtn.setStyle(okBtnNormal));
        okBtn.setOnAction(e -> alertStage.close());

        HBox btnBox = new HBox();
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        btnBox.getChildren().add(okBtn);

        root.getChildren().addAll(header, msgLabel, btnBox);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        alertStage.setScene(scene);
        alertStage.showAndWait();
    }

    private StackPane createSwipeableUserCard(String name) {
        StackPane container = new StackPane();
        container.setUserData(name);

        HBox backLayer = new HBox();
        backLayer.setAlignment(Pos.CENTER_RIGHT);
        backLayer.setStyle("-fx-background-color: #dc2626; -fx-background-radius: 12px;");
        backLayer.setVisible(false);

        Button deleteBtn = new Button("🗑");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 0 18px 0 0; -fx-cursor: hand; -fx-font-size: 15px;");
        backLayer.getChildren().add(deleteBtn);

        HBox frontLayer = new HBox();
        frontLayer.setAlignment(Pos.CENTER_LEFT);
        frontLayer.setPadding(new Insets(12, 16, 12, 16));

        boolean isActive = name.equals(currentUser);
        String activeBg = isDarkMode ? "#27272a" : "#e4e4e7";
        String inactiveBg = isDarkMode ? "#121214" : "#ffffff";
        String activeText = isDarkMode ? "#ffffff" : "#09090b";
        String inactiveText = isDarkMode ? "#a1a1aa" : "#71717a";

        frontLayer.setStyle(isActive ? "-fx-background-color: " + activeBg + "; -fx-background-radius: 12px;" : "-fx-background-color: " + inactiveBg + "; -fx-background-radius: 12px;");

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: " + (isActive ? activeText : inactiveText) + "; -fx-font-weight: " + (isActive ? "bold" : "normal") + "; -fx-font-size: 14px;");
        frontLayer.getChildren().add(nameLabel);

        container.getChildren().addAll(backLayer, frontLayer);

        frontLayer.setOnMouseEntered(e -> {
            if (!name.equals(currentUser)) {
                String hoverBg = isDarkMode ? "#1a1a1e" : "#f4f4f5";
                String hoverText = isDarkMode ? "#e4e4e7" : "#18181b";
                frontLayer.setStyle("-fx-background-color: " + hoverBg + "; -fx-background-radius: 12px; -fx-cursor: hand;");
                nameLabel.setStyle("-fx-text-fill: " + hoverText + "; -fx-font-size: 14px;");
            }
        });
        frontLayer.setOnMouseExited(e -> {
            if (!name.equals(currentUser)) {
                frontLayer.setStyle("-fx-background-color: " + (isDarkMode ? "#121214" : "#ffffff") + "; -fx-background-radius: 12px;");
                nameLabel.setStyle("-fx-text-fill: " + (isDarkMode ? "#a1a1aa" : "#71717a") + "; -fx-font-size: 14px;");
            }
        });

        frontLayer.setOnMouseClicked(e -> {
            if (frontLayer.getTranslateX() == 0) switchUser(name);
        });

        final double[] mouseAnchorX = new double[1];
        frontLayer.setOnMousePressed(e -> mouseAnchorX[0] = e.getSceneX() - frontLayer.getTranslateX());
        frontLayer.setOnMouseDragged(e -> {
            double newX = e.getSceneX() - mouseAnchorX[0];
            if (newX <= 0 && newX >= -65) {
                frontLayer.setTranslateX(newX);
                if (newX < -2) backLayer.setVisible(true);
            }
        });
        frontLayer.setOnMouseReleased(e -> {
            TranslateTransition transition = new TranslateTransition(Duration.millis(200), frontLayer);
            if (frontLayer.getTranslateX() < -35) {
                transition.setToX(-55);
            } else {
                transition.setToX(0);
                transition.setOnFinished(evt -> backLayer.setVisible(false));
            }
            transition.play();
        });

        deleteBtn.setOnAction(e -> {
            if (!showConfirmDialog(I18n.tr("Konto löschen?"), I18n.tr("\"{0}\" und ALLE zugehörigen Einträge wirklich löschen?\nDas kann nicht rückgängig gemacht werden.", name), I18n.tr("Alles löschen"))) {
                loadUsersFromDatabase();
                updateFormState();
                return;
            }
            deleteUserFromDatabase(name);
            loadUsersFromDatabase();
            updateFormState();
        });

        return container;
    }

    private void switchUser(String newUser) {
        currentUser = newUser;
        updateUserCardsVisualsOnly();
        updateFormState();
        entriesContainer.getChildren().clear();
        loadEntriesFromDatabase();
    }

    private StackPane createSwipeableCard(int id, String platform, String url, String username, String passwordToCopy, String totpSecret) {
        StackPane container = new StackPane();

        HBox backLayer = new HBox();
        backLayer.setAlignment(Pos.CENTER_RIGHT);
        backLayer.setStyle("-fx-background-color: #dc2626; -fx-background-radius: 14px;");
        backLayer.setVisible(false);

        Button deleteBtn = new Button(I18n.tr("🗑 Eintrag löschen"));
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 0 25px 0 0; -fx-cursor: hand; -fx-font-size: 14px;");
        backLayer.getChildren().add(deleteBtn);

        HBox frontLayer = new HBox();
        frontLayer.setAlignment(Pos.CENTER_LEFT);
        frontLayer.setPadding(new Insets(16, 24, 16, 24));

        String cardBg = isDarkMode ? "#18181b" : "#ffffff";
        String cardBorder = isDarkMode ? "#27272a" : "#e4e4e7";
        String titleText = isDarkMode ? "#ffffff" : "#09090b";
        String subtitleText = isDarkMode ? "#a1a1aa" : "#71717a";

        frontLayer.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 14px; -fx-border-color: " + cardBorder + "; -fx-border-radius: 14px;");

        VBox textContainer = new VBox(4);
        Label platformLabel = new Label(platform != null ? platform : I18n.tr("Unbekannt"));
        platformLabel.setStyle("-fx-text-fill: " + titleText + "; -fx-font-weight: bold; -fx-font-size: 16px;");

        Label userLabel = new Label(username != null ? username : "");
        userLabel.setStyle("-fx-text-fill: " + subtitleText + "; -fx-font-size: 13px;");

        HBox passBox = new HBox(8);
        passBox.setAlignment(Pos.CENTER_LEFT);
        Label passLabel = new Label("••••••••");
        passLabel.setStyle("-fx-text-fill: " + subtitleText + "; -fx-font-size: 14px;");

        Button eyeBtn = new Button("\uD83D\uDD12");
        eyeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + subtitleText + "; -fx-cursor: hand; -fx-font-size: 13px;");

        boolean[] isVisible = {false};
        eyeBtn.setOnAction(e -> {
            isVisible[0] = !isVisible[0];
            passLabel.setText(isVisible[0] ? (passwordToCopy != null ? passwordToCopy : "") : "••••••••");
            eyeBtn.setText(isVisible[0] ? "\uD83D\uDD13" : "\uD83D\uDD12");
        });
        passBox.getChildren().addAll(passLabel, eyeBtn);

        textContainer.getChildren().addAll(platformLabel, userLabel, passBox);

        if (totpSecret != null && TotpUtil.isPlausible(totpSecret)) {
            String accentColor = isDarkMode ? "#4f46e5" : "#6366f1";

            Label totpBadge = new Label("🔐 2FA:");
            totpBadge.setStyle("-fx-text-fill: " + subtitleText + "; -fx-font-size: 13px;");

            Label codeLabel = new Label("••• •••");
            codeLabel.setId("totpCode");
            codeLabel.setStyle("-fx-text-fill: " + titleText + "; -fx-font-weight: bold; -fx-font-size: 15px; -fx-font-family: 'Consolas', 'Courier New', monospace; -fx-letter-spacing: 2px;");

            ProgressBar ring = new ProgressBar(1.0);
            ring.setPrefSize(70, 8);
            ring.setStyle("-fx-accent: " + accentColor + ";");

            Label secsLabel = new Label("30s");
            secsLabel.setStyle("-fx-text-fill: " + subtitleText + "; -fx-font-size: 12px;");

            Button copyCodeBtn = new Button("📋");
            copyCodeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + subtitleText + "; -fx-cursor: hand; -fx-font-size: 13px;");
            copyCodeBtn.setOnAction(ev -> copyToClipboard(TotpUtil.currentCode(totpSecret)));

            HBox totpRow = new HBox(8);
            totpRow.setAlignment(Pos.CENTER_LEFT);
            totpRow.getChildren().addAll(totpBadge, codeLabel, ring, secsLabel, copyCodeBtn);
            textContainer.getChildren().add(totpRow);

            Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
                long epoch = System.currentTimeMillis() / 1000L;
                long remain = 30 - (epoch % 30);
                String code = TotpUtil.generate(totpSecret, epoch);
                if (!code.isEmpty()) {
                    codeLabel.setText(code.substring(0, 3) + " " + code.substring(3));
                }
                secsLabel.setText(remain + "s");
                ring.setProgress(remain / 30.0);
            }));
            ticker.setCycleCount(Animation.INDEFINITE);
            ticker.play();
            container.setUserData(ticker);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actionButtons = new HBox(10);
        actionButtons.setAlignment(Pos.CENTER_RIGHT);

        String utilityBtnBg = isDarkMode ? "#27272a" : "#e4e4e7";
        String utilityBtnHover = isDarkMode ? "#3f3f46" : "#d4d4d8";
        String utilityBtnText = isDarkMode ? "#ffffff" : "#09090b";

        Button editBtn = new Button("\uD83D\uDCDD");
        editBtn.setStyle("-fx-background-color: " + utilityBtnBg + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;");
        editBtn.setOnMouseEntered(e -> editBtn.setStyle("-fx-background-color: " + utilityBtnHover + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;"));
        editBtn.setOnMouseExited(e -> editBtn.setStyle("-fx-background-color: " + utilityBtnBg + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;"));
        editBtn.setOnAction(e -> {
            showPasswordPromptForEdit(() -> showEditDialog(id, platform, url, username, passwordToCopy, totpSecret));
        });
        actionButtons.getChildren().add(editBtn);

        if (username != null && !username.isEmpty()) {
            Button userCopyBtn = new Button("👤");
            userCopyBtn.setStyle("-fx-background-color: " + utilityBtnBg + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;");
            userCopyBtn.setOnMouseEntered(e -> userCopyBtn.setStyle("-fx-background-color: " + utilityBtnHover + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;"));
            userCopyBtn.setOnMouseExited(e -> userCopyBtn.setStyle("-fx-background-color: " + utilityBtnBg + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;"));
            userCopyBtn.setOnAction(ev -> copyToClipboard(username));
            actionButtons.getChildren().add(userCopyBtn);
        }

        if (url != null && !url.trim().isEmpty()) {
            Button linkBtn = new Button("🌍");
            linkBtn.setStyle("-fx-background-color: " + utilityBtnBg + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;");
            linkBtn.setOnMouseEntered(e -> linkBtn.setStyle("-fx-background-color: " + utilityBtnHover + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;"));
            linkBtn.setOnMouseExited(e -> linkBtn.setStyle("-fx-background-color: " + utilityBtnBg + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;"));
            linkBtn.setOnAction(e -> {
                if (username != null && !username.isEmpty() && passwordToCopy != null && !passwordToCopy.isEmpty()) {
                    showQuickLoginDialog(platform, url, username, passwordToCopy);
                } else if (!openUrl(url)) {
                    showAlert(I18n.tr("Der Link konnte nicht im Browser geöffnet werden."));
                }
            });
            actionButtons.getChildren().add(linkBtn);
        }

        Button copyBtn = new Button(I18n.tr("📋 Kopieren"));
        copyBtn.setUserData("copy");
        String copyBtnNormal = isDarkMode ? "#4f46e5" : "#6366f1";
        String copyBtnHover = isDarkMode ? "#4338ca" : "#4f46e5";
        copyBtn.setStyle("-fx-background-color: " + copyBtnNormal + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand; -fx-font-size: 13px;");

        copyBtn.setOnMouseEntered(e -> {
            if(!copyBtn.getText().startsWith("✓")) copyBtn.setStyle("-fx-background-color: " + copyBtnHover + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand; -fx-font-size: 13px;");
        });
        copyBtn.setOnMouseExited(e -> {
            if(!copyBtn.getText().startsWith("✓")) copyBtn.setStyle("-fx-background-color: " + copyBtnNormal + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand; -fx-font-size: 13px;");
        });

        copyBtn.setOnAction(event -> {
            copyToClipboard(passwordToCopy);
            copyBtn.setText(I18n.tr("✓ Kopiert!"));
            copyBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-font-size: 13px;");

            Timeline resetTextTimeline = new Timeline(new KeyFrame(Duration.seconds(3), evt -> {
                copyBtn.setText(I18n.tr("📋 Kopieren"));
                copyBtn.setStyle("-fx-background-color: " + copyBtnNormal + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand; -fx-font-size: 13px;");
            }));
            resetTextTimeline.play();
        });

        actionButtons.getChildren().add(copyBtn);
        frontLayer.getChildren().addAll(textContainer, spacer, actionButtons);
        container.getChildren().addAll(backLayer, frontLayer);

        final double[] mouseAnchorX = new double[1];
        frontLayer.setOnMousePressed(e -> mouseAnchorX[0] = e.getSceneX() - frontLayer.getTranslateX());
        frontLayer.setOnMouseDragged(e -> {
            double newX = e.getSceneX() - mouseAnchorX[0];
            if (newX <= 0 && newX >= -160) {
                frontLayer.setTranslateX(newX);
                if (newX < -2) backLayer.setVisible(true);
            }
        });
        frontLayer.setOnMouseReleased(e -> {
            TranslateTransition transition = new TranslateTransition(Duration.millis(200), frontLayer);
            if (frontLayer.getTranslateX() < -80) {
                transition.setToX(-150);
            } else {
                transition.setToX(0);
                transition.setOnFinished(evt -> backLayer.setVisible(false));
            }
            transition.play();
        });

        deleteBtn.setOnAction(e -> {
            if (!showConfirmDialog(I18n.tr("Eintrag löschen?"), I18n.tr("\"{0}\" wirklich löschen?\nDas kann nicht rückgängig gemacht werden.", platform != null ? platform : I18n.tr("Unbekannt")), I18n.tr("Löschen"))) return;
            stopTotpTimeline(container);
            deleteFromDatabase(id);
            entriesContainer.getChildren().remove(container);
            refreshEmptyEntriesHint();
        });

        return container;
    }

    private void showQuickLoginDialog(String platform, String url, String username, String password) {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(primaryStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setPrefWidth(400);

        String dropShadow = isDarkMode ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.15)";
        String cardBg = isDarkMode ? "#18181b" : "#ffffff";
        String cardBorder = isDarkMode ? "#3f3f46" : "#e4e4e7";
        String mainText = isDarkMode ? "#ffffff" : "#09090b";
        String subText = isDarkMode ? "#a1a1aa" : "#71717a";
        root.setStyle("-fx-background-color: " + cardBg + "; -fx-border-color: " + cardBorder + "; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);");

        Label icon = new Label("🌐");
        icon.setStyle("-fx-font-size: 34px;");

        Label title = new Label(I18n.tr("Website erkannt"));
        title.setStyle("-fx-text-fill: " + mainText + "; -fx-font-size: 20px; -fx-font-weight: bold;");

        String domain = extractDomain(url);
        Label domainLabel = new Label(domain.isEmpty() ? platform : domain);
        domainLabel.setWrapText(true);
        domainLabel.setStyle("-fx-text-fill: #6366f1; -fx-font-size: 15px; -fx-font-weight: bold;");

        Label userLabel = new Label(username);
        userLabel.setWrapText(true);
        userLabel.setStyle("-fx-text-fill: " + subText + "; -fx-font-size: 13px;");

        ProgressBar stepBar = new ProgressBar(0);
        stepBar.setPrefWidth(Double.MAX_VALUE);
        stepBar.setStyle("-fx-accent: #10b981;");

        Button loginBtn = new Button(I18n.tr("🚀 Automatisch einloggen"));
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 12px; -fx-font-size: 14px;");
        loginBtn.setOnMouseEntered(e -> loginBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 12px; -fx-font-size: 14px;"));
        loginBtn.setOnMouseExited(e -> loginBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 12px; -fx-font-size: 14px;"));

        Button openOnlyBtn = new Button(I18n.tr("Nur Website öffnen"));
        openOnlyBtn.setMaxWidth(Double.MAX_VALUE);
        String openOnlyNormal = "-fx-background-color: transparent; -fx-text-fill: " + subText + "; -fx-cursor: hand; -fx-padding: 8px;";
        String openOnlyHover = "-fx-background-color: " + (isDarkMode ? "#27272a" : "#f4f4f5") + "; -fx-text-fill: " + mainText + "; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px;";
        openOnlyBtn.setStyle(openOnlyNormal);
        openOnlyBtn.setOnMouseEntered(e -> openOnlyBtn.setStyle(openOnlyHover));
        openOnlyBtn.setOnMouseExited(e -> openOnlyBtn.setStyle(openOnlyNormal));

        Timeline cleanupTimeline = new Timeline();
        Timeline countdown = new Timeline();
        dialogStage.setOnHidden(e -> {
            cleanupTimeline.stop();
            countdown.stop();
        });

        final Button mainBtn = loginBtn;

        Runnable passwordStep = () -> {
            countdown.stop();
            copyToClipboard(password);
            icon.setText("✅");
            title.setText(I18n.tr("Passwort kopiert!"));
            domainLabel.setText(I18n.tr("Füge es jetzt im Browser ein (Strg+V)."));
            domainLabel.setStyle("-fx-text-fill: " + mainText + "; -fx-font-size: 14px;");
            stepBar.setProgress(0);
            root.getChildren().remove(stepBar);
            mainBtn.setText(I18n.tr("Fertig"));
            mainBtn.setOnAction(ev -> dialogStage.close());
            openOnlyBtn.setVisible(false);
            cleanupTimeline.stop();
            cleanupTimeline.getKeyFrames().setAll(new KeyFrame(Duration.seconds(6), evt -> dialogStage.close()));
            cleanupTimeline.setCycleCount(1);
            cleanupTimeline.play();
        };

        mainBtn.setOnAction(e -> {
            openUrl(url);
            copyToClipboard(username);
            icon.setText("①");
            title.setText(I18n.tr("Benutzername kopiert"));
            domainLabel.setText(I18n.tr("Füge ihn jetzt im Login-Feld ein (Strg+V)..."));
            domainLabel.setStyle("-fx-text-fill: " + mainText + "; -fx-font-size: 14px;");
            stepBar.setProgress(1.0);
            root.getChildren().add(3, stepBar);

            mainBtn.setText(I18n.tr("② Passwort jetzt kopieren"));
            mainBtn.setOnAction(ev -> passwordStep.run());
            openOnlyBtn.setText(I18n.tr("Abbrechen"));
            openOnlyBtn.setOnAction(ev -> dialogStage.close());

            final long start = System.currentTimeMillis();
            final long duration = 8000L;
            countdown.getKeyFrames().setAll(new KeyFrame(Duration.millis(100), ev -> {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed >= duration) {
                    passwordStep.run();
                } else {
                    stepBar.setProgress(1.0 - (elapsed / (double) duration));
                }
            }));
            countdown.setCycleCount(Animation.INDEFINITE);
            countdown.play();
        });

        openOnlyBtn.setOnAction(e -> {
            openUrl(url);
            dialogStage.close();
        });

        root.getChildren().addAll(icon, title, domainLabel, userLabel, loginBtn, openOnlyBtn);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }

    private boolean openUrl(String url) {
        String candidate = url == null ? "" : url.trim();
        String lower = candidate.toLowerCase();
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            candidate = "https://" + candidate;
            lower = candidate.toLowerCase();
        }
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return false;
        try {
            getHostServices().showDocument(candidate);
            return true;
        } catch (Exception e) { return false; }
    }

    private String extractDomain(String url) {
        if (url == null) return "";
        String u = url.trim().replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^www\\.", "");
        int slash = u.indexOf('/');
        if (slash > 0) u = u.substring(0, slash);
        return u;
    }

    private void initDatabase() {
        Connection conn = db();
        if (conn == null) return;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS config (key TEXT PRIMARY KEY, value TEXT NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS vault_records (id INTEGER PRIMARY KEY AUTOINCREMENT, account_user TEXT NOT NULL, platform TEXT NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL);");

            try {
                stmt.execute("ALTER TABLE vault_records ADD COLUMN url TEXT DEFAULT ''");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE vault_records ADD COLUMN totp_secret TEXT DEFAULT ''");
            } catch (SQLException ignored) {}

        } catch (SQLException e) { System.out.println("DB Init Fehler: " + e.getMessage()); }
    }

    private void setConfig(String key, String value) {
        if (key == null || value == null) return;
        String sql = "INSERT OR REPLACE INTO config (key, value) VALUES (?, ?)";
        Connection conn = db();
        if (conn == null) return;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private String getConfig(String key) {
        if (key == null) return null;
        String sql = "SELECT value FROM config WHERE key = ?";
        Connection conn = db();
        if (conn == null) return null;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("value");
        } catch (SQLException ignored) {}
        return null;
    }

    private boolean isAppInitialized() {
        return "true".equals(getConfig("initialized"));
    }

    private boolean saveUserToDatabase(String name) {
        if (name == null || name.isEmpty()) return false;
        String sql = "INSERT INTO users(name) VALUES(?)";
        Connection conn = db();
        if (conn == null) return false;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    private void loadUsersFromDatabase() {
        usersContainer.getChildren().clear();
        String sql = "SELECT name FROM users ORDER BY id ASC";
        Connection conn = db();
        if (conn == null) { updateFormState(); return; }
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            boolean holdsUsers = false;
            while (rs.next()) {
                holdsUsers = true;
                usersContainer.getChildren().add(createSwipeableUserCard(rs.getString("name")));
            }
            if (holdsUsers && (currentUser == null || currentUser.isEmpty())) {
                try (Statement stmt2 = conn.createStatement(); ResultSet rs2 = stmt2.executeQuery("SELECT name FROM users LIMIT 1")) {
                    if (rs2.next()) switchUser(rs2.getString("name"));
                }
            } else if (!holdsUsers) {
                currentUser = "";
                entriesContainer.getChildren().clear();
                updateFormState();
            }
        } catch (SQLException ignored) {}
    }

    private void deleteUserFromDatabase(String name) {
        if (name == null || name.isEmpty()) return;
        Connection conn = db();
        if (conn == null) return;
        try {
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM users WHERE name = ?")) {
                pstmt.setString(1, name); pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM vault_records WHERE account_user = ?")) {
                pstmt.setString(1, name); pstmt.executeUpdate();
            }
            if (name.equals(currentUser)) {
                currentUser = "";
            }
        } catch (SQLException ignored) {}
    }

    private int saveToDatabase(String user, String platform, String url, String username, String password, String totpSecret) {
        if (sessionKeySpec == null || user == null) return -1;
        String sql = "INSERT INTO vault_records(account_user, platform, url, username, password, totp_secret) VALUES(?,?,?,?,?,?)";
        Connection conn = db();
        if (conn == null) return -1;
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, user);
            pstmt.setString(2, encryptVaultData(platform));
            pstmt.setString(3, (url == null || url.isEmpty()) ? "" : encryptVaultData(url));
            pstmt.setString(4, encryptVaultData(username));
            pstmt.setString(5, encryptVaultData(password));
            pstmt.setString(6, (totpSecret == null || totpSecret.isEmpty()) ? "" : encryptVaultData(totpSecret));
            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ignored) {}
        return -1;
    }

    private void updateInDatabase(int id, String platform, String url, String username, String password, String totpSecret) {
        if (sessionKeySpec == null) return;
        String sql = "UPDATE vault_records SET platform = ?, url = ?, username = ?, password = ?, totp_secret = ? WHERE id = ?";
        Connection conn = db();
        if (conn == null) return;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, encryptVaultData(platform));
            pstmt.setString(2, (url == null || url.isEmpty()) ? "" : encryptVaultData(url));
            pstmt.setString(3, encryptVaultData(username));
            pstmt.setString(4, encryptVaultData(password));
            pstmt.setString(5, (totpSecret == null || totpSecret.isEmpty()) ? "" : encryptVaultData(totpSecret));
            pstmt.setInt(6, id);
            pstmt.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void loadEntriesFromDatabase() {
        if (currentUser == null || currentUser.isEmpty() || sessionKeySpec == null) return;
        stopAllTotpTimelines();
        String sql = "SELECT id, platform, url, username, password, totp_secret FROM vault_records WHERE account_user = ? ORDER BY id DESC";
        Connection conn = db();
        if (conn == null) return;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, currentUser);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String plat = decryptVaultData(rs.getString("platform"));
                String rawUrl = rs.getString("url");
                String url = (rawUrl != null && !rawUrl.isEmpty()) ? decryptVaultData(rawUrl) : "";
                String user = decryptVaultData(rs.getString("username"));
                String pass = decryptVaultData(rs.getString("password"));
                String rawTotp = rs.getString("totp_secret");
                String totp = (rawTotp != null && !rawTotp.isEmpty()) ? decryptVaultData(rawTotp) : "";

                entriesContainer.getChildren().add(createSwipeableCard(rs.getInt("id"), plat, url, user, pass, totp));
            }
        } catch (SQLException ignored) {}
        refreshEmptyEntriesHint();
    }

    private void deleteFromDatabase(int id) {
        String sql = "DELETE FROM vault_records WHERE id = ?";
        Connection conn = db();
        if (conn == null) return;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id); pstmt.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private String encryptVaultData(String plainText) {
        if (plainText == null || plainText.isEmpty()) return "";
        String enc = encryptBytes(plainText.getBytes(StandardCharsets.UTF_8), sessionKeySpec);
        return enc == null ? "" : enc;
    }

    private String decryptVaultData(String cipherText) {
        byte[] data = decryptBytes(cipherText, sessionKeySpec);
        if (data == null) return "";
        return new String(data, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
