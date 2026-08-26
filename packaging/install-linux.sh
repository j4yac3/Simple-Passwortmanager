#!/bin/bash
# ============================================================
#  JM Password Manager - interactive installer for Linux
# ============================================================
APP_NAME="JM Password Manager"
VERSION="1.1.0"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/JMPasswortmanager.jar"
DEFAULT_DIR="$HOME/.local/share/JM-Password-Manager"

clear
echo "=============================================================="
echo "      J M   P A S S W O R D   M A N A G E R   -   S E T U P"
echo "        secure  -  local  -  fast          Version $VERSION"
echo "=============================================================="
echo
echo "  [1/5] ABOUT THIS APP"
echo "  ----------------------------------------------------------"
echo "  A fast, beautiful and 100% local password manager."
echo
echo   " * All passwords are stored encrypted (AES-256-GCM) on this"
echo    "   computer only. No cloud, no accounts, no tracking."
echo  " * Your vault locks itself automatically after 5 minutes."
echo  " * Copied passwords are cleared from the clipboard after 15s."
echo  " * Built-in live 2FA (TOTP) codes and a login assistant."
echo
read -r -p "  Press Enter to continue ... " x

echo
echo "  [2/5] TERMS OF USE"
echo "  ----------------------------------------------------------"
echo "  1. This software is free and provided AS IS, without warranty."
echo "  2. All data stays encrypted on this computer (no cloud)."
echo "  3. Keep your master password AND your 28-character recovery"
echo "     code safe. If both are lost, access is impossible."
echo "  4. Back up your data regularly. No liability for data loss."
echo "  5. Installing means you accept these terms."
echo
read -r -p "  Do you accept these terms? (yes/no): " accept
case "$accept" in
  [Yy]|[Yy][Ee][Ss]) ;;
  *)
    echo
    echo "  Installation cancelled - terms not accepted."
    echo "  You can run this installer again at any time."
    exit 2
    ;;
esac

echo
echo "  [3/5] INSTALL LOCATION"
echo "  ----------------------------------------------------------"
read -r -p "  Install directory [$DEFAULT_DIR]: " DIR
DIR="${DIR:-$DEFAULT_DIR}"
echo "  Will install into: $DIR"

echo
echo "  [4/5] INSTALLING - please wait ..."
mkdir -p "$DIR" || { echo "  ERROR: cannot create $DIR"; exit 1; }
cp "$JAR" "$DIR/" || { echo "  ERROR: cannot copy files"; exit 1; }

cat > "$DIR/start.sh" << EOF
#!/bin/bash
DIR="\$(cd "\$(dirname "\$0")" && pwd)"
exec java -jar "\$DIR/JMPasswortmanager.jar" "\$@"
EOF
chmod +x "$DIR/start.sh"

mkdir -p "$HOME/.local/share/applications"
cat > "$HOME/.local/share/applications/jm-password-manager.desktop" << EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=$APP_NAME
Comment=Secure 100% local password manager
Exec=$DIR/start.sh
Path=$DIR
Icon=security-high
Terminal=false
Categories=Utility;Security;
EOF

echo "  ----------------------------------------------------------"
if command -v java >/dev/null 2>&1; then
  echo "  [OK] Java found: $(java -version 2>&1 | head -n 1)"
else
  echo "  [!] Java 17+ is NOT installed yet. Please install it, e.g.:"
  echo "      Ubuntu/Debian: sudo apt install openjdk-17-jre"
  echo "      Fedora:        sudo dnf install java-17-openjdk"
fi
echo "  [OK] Files copied to: $DIR"
echo "  [OK] Start menu entry created (jm-password-manager)"

echo
echo "  [5/5] T H A N K   Y O U !"
echo "  ----------------------------------------------------------"
echo "  Thank you for using JM Password Manager!"
echo "  Start it from your applications menu, or with: $DIR/start.sh"
echo
echo "  IMPORTANT: On first start, write down your 28-character"
echo "  recovery code - it is the only way to reset a forgotten"
echo "  master password."
echo
read -r -p "  Launch the app now? (y/n): " launch
case "$launch" in
  [Yy]*)
    nohup "$DIR/start.sh" >/dev/null 2>&1 &
    echo "  App is starting ..."
    ;;
  *) echo "  You can start it any time." ;;
esac
exit 0
