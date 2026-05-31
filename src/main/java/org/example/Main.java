package org.example;

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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
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

    static {
        // Speichert die Datenbank sicher im Benutzerordner (z.B. C:\Users\DeinName\.jm-passwortmanager\)
        String userHome = System.getProperty("user.home");
        java.io.File appDir = new java.io.File(userHome, ".jm-passwortmanager");
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        DB_URL = "jdbc:sqlite:" + new java.io.File(appDir, "vault.db").getAbsolutePath();
    }

    private String currentUser = "";
    private boolean isSidebarVisible = true;
    private boolean isDarkMode = true;

    private SecretKeySpec sessionKeySpec;
    private Stage primaryStage;
    private BorderPane mainLayout;

    private VBox sidebar;
    private Label menuTitle;
    private Label titleLabel;
    private VBox inputForm;
    private TextField platformInput;
    private TextField urlInput;
    private TextField userInput;
    private TextField passwordInput;
    private Button generateBtn;
    private Region separator;
    private Button toggleSidebarBtn;
    private Button themeToggleBtn;
    private Button addBtn;
    private Button addUserBtn;

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        initDatabase();

        try {
            InputStream iconStream = getClass().getResourceAsStream("/icon.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ignored) {}

        primaryStage.setTitle("JM Passwortmanager");
        primaryStage.setResizable(true); // Flexibler gemacht
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        if (isAppInitialized()) {
            showLoginScreen();
        } else {
            showSetupScreen();
        }
    }

    private void showSetupScreen() {
        VBox root = createAuthLayout("JM Passwortmanager", "Erstelle ein sicheres Master-Passwort, um deinen Tresor zu schützen.");

        PasswordField passInput = new PasswordField();
        passInput.setPromptText("Master-Passwort");
        styleAuthField(passInput);

        PasswordField passConfirm = new PasswordField();
        passConfirm.setPromptText("Passwort bestätigen");
        styleAuthField(passConfirm);

        Button submitBtn = createAuthButton("Tresor initialisieren");
        submitBtn.setOnAction(e -> {
            String p1 = passInput.getText();
            String p2 = passConfirm.getText();

            if (p1 == null || p1.length() < 6) {
                showAlert("Das Passwort muss mindestens 6 Zeichen lang sein.");
                return;
            }
            if (!p1.equals(p2)) {
                showAlert("Die Passwörter stimmen nicht überein.");
                return;
            }

            try {
                String recoveryCode = String.format("%06d", new SecureRandom().nextInt(1000000));
                setupVaultSystem(p1, recoveryCode);
                showRecoveryCodeScreen(recoveryCode, this::buildAndShowMainUI);
            } catch (Exception ex) {
                showAlert("Systemfehler bei der Initialisierung.");
            }
        });

        root.getChildren().addAll(passInput, passConfirm, submitBtn);
        primaryStage.setScene(new Scene(root, 1280, 720));
        primaryStage.show();
        primaryStage.centerOnScreen();
    }

    private void showLoginScreen() {
        VBox root = createAuthLayout("Willkommen zurück", "Bitte gib dein Master-Passwort ein, um den Tresor zu entsperren.");

        PasswordField passInput = new PasswordField();
        passInput.setPromptText("Master-Passwort");
        styleAuthField(passInput);

        Button loginBtn = createAuthButton("Entsperren");
        loginBtn.setOnAction(e -> {
            if (tryUnlockWithPassword(passInput.getText())) {
                buildAndShowMainUI();
            } else {
                showAlert("Falsches Master-Passwort!");
                passInput.clear();
            }
        });

        Button forgotBtn = new Button("Passwort vergessen?");
        forgotBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #a1a1aa; -fx-cursor: hand; -fx-underline: true;");
        forgotBtn.setOnMouseEntered(e -> forgotBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffffff; -fx-cursor: hand; -fx-underline: true;"));
        forgotBtn.setOnMouseExited(e -> forgotBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #a1a1aa; -fx-cursor: hand; -fx-underline: true;"));
        forgotBtn.setOnAction(e -> showRecoveryScreen());

        root.getChildren().addAll(passInput, loginBtn, forgotBtn);
        primaryStage.setScene(new Scene(root, 1280, 720));
        primaryStage.show();
        primaryStage.centerOnScreen();
    }

    private void showRecoveryScreen() {
        VBox root = createAuthLayout("Passwort zurücksetzen", "Gib deinen 6-stelligen Recovery-Code ein.");

        TextField codeInput = new TextField();
        codeInput.setPromptText("6-stelliger Code");
        styleAuthField(codeInput);

        PasswordField newPassInput = new PasswordField();
        newPassInput.setPromptText("Neues Master-Passwort");
        styleAuthField(newPassInput);

        Button resetBtn = createAuthButton("Passwort zurücksetzen");
        resetBtn.setOnAction(e -> {
            String code = codeInput.getText() != null ? codeInput.getText().trim() : "";
            String newPass = newPassInput.getText() != null ? newPassInput.getText() : "";

            if (newPass.length() < 6) {
                showAlert("Das neue Passwort muss mindestens 6 Zeichen lang sein.");
                return;
            }

            if (tryUnlockWithRecoveryCodeAndReset(code, newPass)) {
                showAlert("Passwort erfolgreich zurückgesetzt!");
                buildAndShowMainUI();
            } else {
                showAlert("Falscher oder ungültiger Recovery-Code.");
            }
        });

        Button backBtn = new Button("Zurück zum Login");
        backBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #a1a1aa; -fx-cursor: hand;");
        backBtn.setOnAction(e -> showLoginScreen());

        root.getChildren().addAll(codeInput, newPassInput, resetBtn, backBtn);
        primaryStage.setScene(new Scene(root, 1280, 720));
    }

    private void showRecoveryCodeScreen(String code, Runnable onComplete) {
        VBox root = createAuthLayout("WICHTIG: Recovery-Code", "Speichere diesen 6-stelligen Code sicher ab!\nEr ist die einzige Möglichkeit, dein Passwort zurückzusetzen.");

        Label codeLabel = new Label(code);
        codeLabel.setStyle("-fx-font-size: 48px; -fx-font-weight: bold; -fx-text-fill: #10b981; -fx-letter-spacing: 5px;");

        Button copyBtn = new Button("Code kopieren");
        copyBtn.setStyle("-fx-background-color: #27272a; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 8; -fx-cursor: hand;");
        copyBtn.setOnAction(e -> {
            copyToClipboard(code);
            copyBtn.setText("Kopiert!");
            copyBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 8; -fx-cursor: hand;");
        });

        Button proceedBtn = createAuthButton("Ich habe den Code sicher gespeichert");
        proceedBtn.setOnAction(e -> onComplete.run());

        root.getChildren().addAll(codeLabel, copyBtn, proceedBtn);
        primaryStage.setScene(new Scene(root, 1280, 720));
    }

    private void setupVaultSystem(String password, String recoveryCode) throws Exception {
        byte[] vaultKey = new byte[32];
        new SecureRandom().nextBytes(vaultKey);

        byte[] saltPass = new byte[16];
        new SecureRandom().nextBytes(saltPass);
        byte[] kekPass = deriveKey(password, saltPass);

        byte[] saltRecovery = new byte[16];
        new SecureRandom().nextBytes(saltRecovery);
        byte[] kekRecovery = deriveKey(recoveryCode, saltRecovery);

        String encryptedVaultKeyPass = encryptBytes(vaultKey, new SecretKeySpec(kekPass, "AES"));
        String encryptedVaultKeyRecovery = encryptBytes(vaultKey, new SecretKeySpec(kekRecovery, "AES"));

        setConfig("salt_pass", Base64.getEncoder().encodeToString(saltPass));
        setConfig("salt_recovery", Base64.getEncoder().encodeToString(saltRecovery));
        setConfig("enc_vk_pass", encryptedVaultKeyPass);
        setConfig("enc_vk_recovery", encryptedVaultKeyRecovery);
        setConfig("initialized", "true");

        this.sessionKeySpec = new SecretKeySpec(vaultKey, "AES");
    }

    private boolean tryUnlockWithPassword(String password) {
        if (password == null || password.isEmpty()) return false;
        try {
            byte[] saltPass = Base64.getDecoder().decode(getConfig("salt_pass"));
            String encVkPass = getConfig("enc_vk_pass");

            byte[] kekPass = deriveKey(password, saltPass);
            byte[] vaultKey = decryptBytes(encVkPass, new SecretKeySpec(kekPass, "AES"));

            if (vaultKey != null) {
                this.sessionKeySpec = new SecretKeySpec(vaultKey, "AES");
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

            byte[] kekRecovery = deriveKey(code, saltRecovery);
            byte[] vaultKey = decryptBytes(encVkRecovery, new SecretKeySpec(kekRecovery, "AES"));

            if (vaultKey == null) return false;

            byte[] newSaltPass = new byte[16];
            new SecureRandom().nextBytes(newSaltPass);
            byte[] newKekPass = deriveKey(newPassword, newSaltPass);
            String newEncVkPass = encryptBytes(vaultKey, new SecretKeySpec(newKekPass, "AES"));

            setConfig("salt_pass", Base64.getEncoder().encodeToString(newSaltPass));
            setConfig("enc_vk_pass", newEncVkPass);

            this.sessionKeySpec = new SecretKeySpec(vaultKey, "AES");
            return true;
        } catch (Exception e) { return false; }
    }

    private byte[] deriveKey(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }

    private String encryptBytes(byte[] data, SecretKeySpec key) {
        if (data == null) return null;
        try {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            byte[] enc = cipher.doFinal(data);
            byte[] combined = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(enc, 0, combined, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) { return null; }
    }

    private byte[] decryptBytes(String dataBase64, SecretKeySpec key) {
        if (dataBase64 == null || dataBase64.isEmpty()) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(dataBase64);
            byte[] iv = new byte[16];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            byte[] enc = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, enc, 0, enc.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            return cipher.doFinal(enc);
        } catch (Exception e) { return null; }
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
        titleLabel = new Label("Passwortmanager");

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

        topHeader.getChildren().addAll(toggleSidebarBtn, iconLabel, titleLabel, topSpacer, themeToggleBtn);

        inputForm = new VBox(14);
        inputForm.setPadding(new Insets(24));
        platformInput = createStyledTextField("Plattform (z.B. Google, GitHub)");
        urlInput = createStyledTextField("Website-Link (z.B. https://github.com) - Optional");
        userInput = createStyledTextField("Benutzername / E-Mail");

        HBox passwordBox = new HBox(10);
        passwordInput = createStyledTextField("Passwort");
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
            String user = userInput.getText() != null ? userInput.getText().trim() : "";
            String pass = passwordInput.getText() != null ? passwordInput.getText().trim() : "";

            if (!platform.isEmpty() && !user.isEmpty() && !pass.isEmpty()) {
                int id = saveToDatabase(currentUser, platform, url, user, pass);
                if (id != -1) {
                    StackPane newCard = createSwipeableCard(id, platform, url, user, pass);
                    entriesContainer.getChildren().add(0, newCard);
                    platformInput.clear();
                    urlInput.clear();
                    userInput.clear();
                    passwordInput.clear();
                }
            } else {
                showAlert("Plattform, Benutzername und Passwort dürfen nicht leer sein.");
            }
        });

        inputForm.getChildren().addAll(platformInput, urlInput, userInput, passwordBox, addBtn);
        separator = new Region();
        separator.setPrefHeight(1);

        ScrollPane scrollPane = new ScrollPane(entriesContainer);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-control-inner-background: transparent; -fx-vbar-policy: as-needed; -fx-hbar-policy: never;");

        centerContainer.getChildren().addAll(topHeader, inputForm, separator, scrollPane);
        mainLayout.setCenter(centerContainer);

        setupButtonHoverListeners();
        loadUsersFromDatabase();
        applyTheme();

        Scene mainScene = new Scene(mainLayout, 1280, 720);
        primaryStage.setScene(mainScene);
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
    }

    private void updateFormState() {
        boolean noUser = currentUser == null || currentUser.isEmpty();

        platformInput.setDisable(noUser);
        urlInput.setDisable(noUser);
        userInput.setDisable(noUser);
        passwordInput.setDisable(noUser);
        generateBtn.setDisable(noUser);

        updateTextFieldStyleImmediate(platformInput);
        updateTextFieldStyleImmediate(urlInput);
        updateTextFieldStyleImmediate(userInput);
        updateTextFieldStyleImmediate(passwordInput);

        if (noUser) {
            addBtn.setText("👤 Bitte zuerst einen Workspace erstellen");
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
            addBtn.setText("＋ Eintrag sicher verschlüsseln");
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
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #09090b;");

        Label icon = new Label("🔒");
        icon.setStyle("-fx-font-size: 48px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: bold;");

        Label subLabel = new Label(subtitle);
        subLabel.setStyle("-fx-text-fill: #a1a1aa; -fx-font-size: 14px; -fx-text-alignment: center;");

        root.getChildren().addAll(icon, titleLabel, subLabel);
        return root;
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

                HBox buttonBox = (HBox) frontLayer.getChildren().get(2);
                for (Node btnNode : buttonBox.getChildren()) {
                    if (btnNode instanceof Button) {
                        Button btn = (Button) btnNode;
                        if (btn.getText().contains("Kopieren")) {
                            btn.setStyle("-fx-background-color: " + btnBgNormal + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand; -fx-font-size: 13px;");
                        } else if (!btn.getText().contains("Kopiert")) {
                            btn.setStyle("-fx-background-color: " + utilityBtnBg + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;");
                        }
                    }
                }
            }
        }
    }

    // --- Hilfsmethode für sicheres Kopieren (Auto-Clear nach 15 Sekunden) ---
    private void copyToClipboard(String text) {
        if(text == null) return;
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);

        // Sicherheitsfeature: Clipboard nach 15 Sekunden löschen
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
        dialogStage.initOwner(primaryStage); // Verhindert Bugs bei Multimonitor/Resizing
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(15);
        root.setPadding(new Insets(24));
        root.setPrefWidth(380);

        String dropShadow = isDarkMode ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.15)";
        root.setStyle(isDarkMode ?
                "-fx-background-color: #18181b; -fx-border-color: #3f3f46; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);" :
                "-fx-background-color: #ffffff; -fx-border-color: #e4e4e7; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);");

        Label title = new Label("Passwort generieren");
        title.setStyle(isDarkMode ? "-fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-font-weight: bold;" : "-fx-text-fill: #09090b; -fx-font-size: 20px; -fx-font-weight: bold;");

        TextField previewField = new TextField();
        previewField.setEditable(false);
        updateTextFieldStyleImmediate(previewField);

        Button refreshBtn = new Button("🔄 Neu würfeln");
        refreshBtn.setStyle(isDarkMode ?
                "-fx-background-color: #27272a; -fx-text-fill: #ffffff; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 12px; -fx-font-size: 13px;" :
                "-fx-background-color: #e4e4e7; -fx-text-fill: #09090b; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 8px 12px; -fx-font-size: 13px;");

        HBox previewBox = new HBox(10);
        HBox.setHgrow(previewField, Priority.ALWAYS);
        previewBox.getChildren().addAll(previewField, refreshBtn);

        HBox lengthBox = new HBox(10);
        lengthBox.setAlignment(Pos.CENTER_LEFT);
        Label lengthLabel = new Label("Länge: 16");
        lengthLabel.setPrefWidth(70);
        lengthLabel.setStyle(isDarkMode ? "-fx-text-fill: #a1a1aa; -fx-font-weight: bold;" : "-fx-text-fill: #71717a; -fx-font-weight: bold;");

        Slider lengthSlider = new Slider(8, 64, 16);
        lengthSlider.setBlockIncrement(1);
        HBox.setHgrow(lengthSlider, Priority.ALWAYS);
        lengthBox.getChildren().addAll(lengthLabel, lengthSlider);

        CheckBox cbUpper = new CheckBox("Großbuchstaben (A-Z)");
        CheckBox cbLower = new CheckBox("Kleinbuchstaben (a-z)");
        CheckBox cbNumbers = new CheckBox("Zahlen (0-9)");
        CheckBox cbSpecial = new CheckBox("Sonderzeichen (!@#...)");

        String cbStyle = isDarkMode ? "-fx-text-fill: #e4e4e7; -fx-font-size: 14px;" : "-fx-text-fill: #18181b; -fx-font-size: 14px;";
        cbUpper.setStyle(cbStyle); cbLower.setStyle(cbStyle); cbNumbers.setStyle(cbStyle); cbSpecial.setStyle(cbStyle);

        cbUpper.setSelected(true); cbLower.setSelected(true); cbNumbers.setSelected(true); cbSpecial.setSelected(true);

        VBox optionsBox = new VBox(10);
        optionsBox.setPadding(new Insets(10, 0, 10, 0));
        optionsBox.getChildren().addAll(cbUpper, cbLower, cbNumbers, cbSpecial);

        Runnable updatePreview = () -> {
            int len = (int) lengthSlider.getValue();
            lengthLabel.setText("Länge: " + len);
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

        Button cancelBtn = new Button("Abbrechen");
        Button applyBtn = new Button("Übernehmen");

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

        Label title = new Label("Neuer Workspace");
        title.setStyle(isDarkMode ? "-fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-font-weight: bold;" : "-fx-text-fill: #09090b; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label subtitle = new Label("Gib einen Namen für das neue Konto ein.");
        subtitle.setStyle(isDarkMode ? "-fx-text-fill: #a1a1aa; -fx-font-size: 13px;" : "-fx-text-fill: #71717a; -fx-font-size: 13px;");

        TextField nameInput = new TextField();
        nameInput.setPromptText("z.B. Arbeit, Privat...");
        updateTextFieldStyleImmediate(nameInput);
        nameInput.focusedProperty().addListener((obs, oldVal, newVal) -> updateTextFieldStyleImmediate(nameInput));

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        Button cancelBtn = new Button("Abbrechen");
        Button createBtn = new Button("Erstellen");

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
                    showAlert("Dieses Konto existiert bereits!");
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

        Label title = new Label("Sicherheitsprüfung");
        title.setStyle(isDarkMode ? "-fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-font-weight: bold;" : "-fx-text-fill: #09090b; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label subtitle = new Label("Bitte Master-Passwort eingeben:");
        subtitle.setStyle(isDarkMode ? "-fx-text-fill: #a1a1aa; -fx-font-size: 13px;" : "-fx-text-fill: #71717a; -fx-font-size: 13px;");

        PasswordField passInput = new PasswordField();
        passInput.setPromptText("Master-Passwort");
        updateTextFieldStyleImmediate(passInput);
        passInput.focusedProperty().addListener((obs, oldVal, newVal) -> updateTextFieldStyleImmediate(passInput));

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Abbrechen");
        Button confirmBtn = new Button("Bestätigen");

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

    private void showEditDialog(int id, String oldPlat, String oldUrl, String oldUser, String oldPass) {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(primaryStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        // Dynamische Höhe durch Region.USE_COMPUTED_SIZE
        VBox root = new VBox(0);
        root.setPrefWidth(420);
        root.setMinHeight(Region.USE_PREF_SIZE);

        String dropShadow = isDarkMode ? "rgba(0,0,0,0.6)" : "rgba(0,0,0,0.15)";
        root.setStyle(isDarkMode ?
                "-fx-background-color: #18181b; -fx-border-color: #3f3f46; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);" :
                "-fx-background-color: #ffffff; -fx-border-color: #e4e4e7; -fx-border-width: 1px; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-effect: dropshadow(three-pass-box, " + dropShadow + ", 25, 0, 0, 10);");

        VBox header = new VBox(5);
        header.setPadding(new Insets(24, 24, 16, 24));
        Label title = new Label("Eintrag bearbeiten");
        title.setStyle(isDarkMode ? "-fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-font-weight: bold;" : "-fx-text-fill: #09090b; -fx-font-size: 20px; -fx-font-weight: bold;");
        Label subTitle = new Label("Passe die Felder an und speichere die Änderungen.");
        subTitle.setStyle(isDarkMode ? "-fx-text-fill: #a1a1aa; -fx-font-size: 13px;" : "-fx-text-fill: #71717a; -fx-font-size: 13px;");
        header.getChildren().addAll(title, subTitle);

        VBox formBox = new VBox(16);
        formBox.setPadding(new Insets(0, 24, 16, 24));

        TextField platField = createStyledTextField("Plattform");
        platField.setText(oldPlat);

        TextField urlField = createStyledTextField("URL (Optional)");
        urlField.setText(oldUrl);

        TextField userField = createStyledTextField("Benutzername");
        userField.setText(oldUser);

        HBox passBox = new HBox(10);
        TextField passField = createStyledTextField("Passwort");
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

        formBox.getChildren().addAll(platField, urlField, userField, passBox);

        ScrollPane scrollPane = new ScrollPane(formBox);
        scrollPane.setFitToWidth(true);
        // Style angepasst für den Edit-Dialog (keine abgerundeten Ecken am Scrollpane selbst nötig)
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-control-inner-background: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(16, 24, 24, 24));

        Button cancelBtn = new Button("Abbrechen");
        Button saveBtn = new Button("Speichern");

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
            String n = userField.getText() != null ? userField.getText().trim() : "";
            String pw = passField.getText() != null ? passField.getText().trim() : "";

            if (!p.isEmpty() && !n.isEmpty() && !pw.isEmpty()) {
                updateInDatabase(id, p, u, n, pw);
                entriesContainer.getChildren().clear();
                loadEntriesFromDatabase();
                dialogStage.close();
            } else {
                showAlert("Plattform, Benutzername und Passwort dürfen nicht leer sein.");
            }
        });

        buttonBox.getChildren().addAll(cancelBtn, saveBtn);
        root.getChildren().addAll(header, scrollPane, buttonBox);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialogStage.setScene(scene);

        Platform.runLater(platField::requestFocus);
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
        Label title = new Label("Hinweis");
        title.setStyle(isDarkMode ? "-fx-text-fill: #ffffff; -fx-font-size: 18px; -fx-font-weight: bold;" : "-fx-text-fill: #09090b; -fx-font-size: 18px; -fx-font-weight: bold;");
        header.getChildren().addAll(icon, title);

        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.setStyle(isDarkMode ? "-fx-text-fill: #a1a1aa; -fx-font-size: 14px;" : "-fx-text-fill: #71717a; -fx-font-size: 14px;");

        Button okBtn = new Button("Verstanden");
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

    private StackPane createSwipeableCard(int id, String platform, String url, String username, String passwordToCopy) {
        StackPane container = new StackPane();

        HBox backLayer = new HBox();
        backLayer.setAlignment(Pos.CENTER_RIGHT);
        backLayer.setStyle("-fx-background-color: #dc2626; -fx-background-radius: 14px;");
        backLayer.setVisible(false);

        Button deleteBtn = new Button("🗑 Eintrag löschen");
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
        Label platformLabel = new Label(platform != null ? platform : "Unbekannt");
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
            showPasswordPromptForEdit(() -> showEditDialog(id, platform, url, username, passwordToCopy));
        });
        actionButtons.getChildren().add(editBtn);

        if (url != null && !url.trim().isEmpty()) {
            Button linkBtn = new Button("🌍");
            linkBtn.setStyle("-fx-background-color: " + utilityBtnBg + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;");
            linkBtn.setOnMouseEntered(e -> linkBtn.setStyle("-fx-background-color: " + utilityBtnHover + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;"));
            linkBtn.setOnMouseExited(e -> linkBtn.setStyle("-fx-background-color: " + utilityBtnBg + "; -fx-text-fill: " + utilityBtnText + "; -fx-background-radius: 8px; -fx-padding: 8px 12px; -fx-cursor: hand; -fx-font-size: 13px;"));
            linkBtn.setOnAction(e -> {
                try {
                    String finalUrl = url.trim();
                    if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                        finalUrl = "https://" + finalUrl;
                    }
                    getHostServices().showDocument(finalUrl);
                } catch (Exception ex) {
                    showAlert("Der Link konnte nicht im Browser geöffnet werden.");
                }
            });
            actionButtons.getChildren().add(linkBtn);
        }

        Button copyBtn = new Button("📋 Kopieren");
        String copyBtnNormal = isDarkMode ? "#4f46e5" : "#6366f1";
        String copyBtnHover = isDarkMode ? "#4338ca" : "#4f46e5";
        copyBtn.setStyle("-fx-background-color: " + copyBtnNormal + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand; -fx-font-size: 13px;");

        copyBtn.setOnMouseEntered(e -> {
            if(!copyBtn.getText().contains("Kopiert")) copyBtn.setStyle("-fx-background-color: " + copyBtnHover + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand; -fx-font-size: 13px;");
        });
        copyBtn.setOnMouseExited(e -> {
            if(!copyBtn.getText().contains("Kopiert")) copyBtn.setStyle("-fx-background-color: " + copyBtnNormal + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand; -fx-font-size: 13px;");
        });

        copyBtn.setOnAction(event -> {
            copyToClipboard(passwordToCopy);
            copyBtn.setText("✓ Kopiert!");
            copyBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-font-size: 13px;");

            // Setzt den Button-Text nach 3 Sekunden optisch wieder zurück
            Timeline resetTextTimeline = new Timeline(new KeyFrame(Duration.seconds(3), evt -> {
                copyBtn.setText("📋 Kopieren");
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
            deleteFromDatabase(id);
            entriesContainer.getChildren().remove(container);
        });

        return container;
    }

    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS config (key TEXT PRIMARY KEY, value TEXT NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL);");
            stmt.execute("CREATE TABLE IF NOT EXISTS vault_records (id INTEGER PRIMARY KEY AUTOINCREMENT, account_user TEXT NOT NULL, platform TEXT NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL);");

            // Migration für das URL-Feld (Ignorieren, falls die Spalte schon existiert)
            try {
                stmt.execute("ALTER TABLE vault_records ADD COLUMN url TEXT DEFAULT ''");
            } catch (SQLException ignored) {}

        } catch (SQLException e) { System.out.println("DB Init Fehler: " + e.getMessage()); }
    }

    private void setConfig(String key, String value) {
        if (key == null || value == null) return;
        String sql = "INSERT OR REPLACE INTO config (key, value) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private String getConfig(String key) {
        if (key == null) return null;
        String sql = "SELECT value FROM config WHERE key = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    private void loadUsersFromDatabase() {
        usersContainer.getChildren().clear();
        String sql = "SELECT name FROM users ORDER BY id ASC";
        try (Connection conn = DriverManager.getConnection(DB_URL); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
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
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
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

    private int saveToDatabase(String user, String platform, String url, String username, String password) {
        if (sessionKeySpec == null || user == null) return -1;
        String sql = "INSERT INTO vault_records(account_user, platform, url, username, password) VALUES(?,?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, user);
            pstmt.setString(2, encryptVaultData(platform));
            pstmt.setString(3, (url == null || url.isEmpty()) ? "" : encryptVaultData(url));
            pstmt.setString(4, encryptVaultData(username));
            pstmt.setString(5, encryptVaultData(password));
            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException ignored) {}
        return -1;
    }

    private void updateInDatabase(int id, String platform, String url, String username, String password) {
        if (sessionKeySpec == null) return;
        String sql = "UPDATE vault_records SET platform = ?, url = ?, username = ?, password = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, encryptVaultData(platform));
            pstmt.setString(2, (url == null || url.isEmpty()) ? "" : encryptVaultData(url));
            pstmt.setString(3, encryptVaultData(username));
            pstmt.setString(4, encryptVaultData(password));
            pstmt.setInt(5, id);
            pstmt.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void loadEntriesFromDatabase() {
        if (currentUser == null || currentUser.isEmpty() || sessionKeySpec == null) return;
        String sql = "SELECT id, platform, url, username, password FROM vault_records WHERE account_user = ? ORDER BY id DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, currentUser);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String plat = decryptVaultData(rs.getString("platform"));
                String rawUrl = rs.getString("url");
                String url = (rawUrl != null && !rawUrl.isEmpty()) ? decryptVaultData(rawUrl) : "";
                String user = decryptVaultData(rs.getString("username"));
                String pass = decryptVaultData(rs.getString("password"));

                entriesContainer.getChildren().add(createSwipeableCard(rs.getInt("id"), plat, url, user, pass));
            }
        } catch (SQLException ignored) {}
    }

    private void deleteFromDatabase(int id) {
        String sql = "DELETE FROM vault_records WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id); pstmt.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private String encryptVaultData(String plainText) {
        if (plainText == null || plainText.isEmpty()) return "";
        try {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, sessionKeySpec, ivSpec);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) { return ""; }
    }

    private String decryptVaultData(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) return "";
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[16];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            int encryptedSize = combined.length - iv.length;
            byte[] encryptedBytes = new byte[encryptedSize];
            System.arraycopy(combined, iv.length, encryptedBytes, 0, encryptedSize);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, sessionKeySpec, ivSpec);
            return new String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}