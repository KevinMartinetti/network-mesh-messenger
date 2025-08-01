# MeshChat Android

🔐 **Secure TCP Mesh Chat Application for Android**

MeshChat is a modern Android application that enables secure peer-to-peer communication through TCP mesh networking. Built with Kotlin and Jetpack Compose, it features end-to-end encryption, beautiful Material Design 3 UI, and robust mesh networking capabilities.

## ✨ Features

### 🔒 Security
- **AES-256-GCM encryption** for message content
- **RSA-4096 key exchange** for secure session establishment
- **Digital signatures** for message authentication
- **Perfect forward secrecy** with session keys
- **BouncyCastle cryptography** for enhanced security

### 🌐 Networking
- **TCP mesh networking** with P2P connections
- **Server/Client modes** - create or join networks
- **Automatic peer discovery** and connection management
- **Heartbeat mechanism** for connection monitoring
- **Foreground service** for persistent connections

### 🎨 User Interface
- **Material Design 3** with dynamic theming
- **Modern Jetpack Compose** UI
- **Real-time messaging** with smooth animations
- **User presence indicators** and status
- **Responsive design** for various screen sizes

### 📱 Android Features
- **Android 7.0+ support** (API level 24+)
- **Proper permissions handling**
- **Background service** for mesh networking
- **Notification system** for service status
- **File sharing capabilities** (planned)

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- Android SDK 34
- JDK 17 or newer
- Device/emulator running Android 7.0+ (API 24+)

### Building the Project

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/meshchat-android.git
   cd meshchat-android
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Build and run**
   ```bash
   ./gradlew assembleDebug
   # or use Android Studio's build system
   ```

### Using GitHub Actions

The project includes automated CI/CD workflows:

- **Build workflow** - Automatically builds APKs on push/PR
- **Code quality** - Runs linting and code analysis
- **Release workflow** - Creates releases with APK artifacts

## 📖 Usage

### Creating a Network
1. Open MeshChat
2. Enter your username
3. Select "Create Network" tab
4. Choose a port (default: 8080)
5. Tap "Create Network"
6. Share your IP address with others

### Joining a Network
1. Open MeshChat
2. Enter your username
3. Select "Connect to Network" tab
4. Enter the server IP address
5. Enter the port number
6. Tap "Connect"

### Chatting
- Type messages in the input field
- Tap the send button or press enter
- View connected users by tapping the users icon
- Messages are automatically encrypted end-to-end

## 🏗️ Architecture

### Project Structure
```
app/src/main/java/com/meshchat/android/
├── crypto/           # Encryption and key management
├── data/            # Data models (Message, User)
├── network/         # TCP networking and mesh management
├── ui/              # Compose UI components
│   ├── screens/     # Main app screens
│   ├── theme/       # Material Design theming
│   └── viewmodel/   # ViewModels for state management
└── MainActivity.kt  # Main activity
```

### Key Components

#### CryptoManager
- Handles AES-256-GCM encryption/decryption
- Manages RSA-4096 key pairs
- Provides digital signatures
- Secure key storage

#### MeshNetworkManager
- TCP socket management
- Peer connection handling
- Message routing and forwarding
- Encryption integration

#### MeshNetworkService
- Foreground service for background networking
- Connection persistence
- System notifications

## 🔧 Configuration

### Network Settings
- **Default Port**: 8080
- **Connection Timeout**: 10 seconds
- **Heartbeat Interval**: 30 seconds
- **Max Message Size**: 8KB

### Security Settings
- **RSA Key Size**: 4096 bits
- **AES Key Size**: 256 bits
- **GCM Tag Length**: 128 bits
- **Session Key Rotation**: Per connection

## 🛡️ Security Considerations

### Threat Model
- **Man-in-the-middle attacks**: Prevented by RSA key exchange
- **Message tampering**: Prevented by digital signatures
- **Eavesdropping**: Prevented by AES-256-GCM encryption
- **Replay attacks**: Prevented by timestamps and nonces

### Best Practices
- Keys are stored securely using Android SharedPreferences
- Network traffic uses secure protocols
- Input validation prevents injection attacks
- Proper error handling prevents information leakage

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Write unit tests for new features

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **BouncyCastle** for cryptographic functions
- **Jetpack Compose** for modern UI development
- **Material Design 3** for beautiful theming
- **OkHttp** for networking capabilities

## 📞 Support

If you encounter any issues or have questions:

1. Check the [Issues](https://github.com/yourusername/meshchat-android/issues) page
2. Create a new issue with detailed information
3. Include device information and logs when possible

## 🗺️ Roadmap

- [ ] File and image sharing
- [ ] Voice messages
- [ ] Group chat management
- [ ] QR code connection
- [ ] Bluetooth mesh support
- [ ] Offline message storage
- [ ] Push notifications
- [ ] Multi-language support

---

**Made with ❤️ for secure communication**
