# Contributing to FastRecorder

Thank you for your interest in contributing to FastRecorder!

## 🚀 Getting Started

1. Fork the repository
2. Clone your fork
3. Create a feature branch
4. Make your changes
5. Submit a pull request

## 📋 Development Setup

### Prerequisites
- Android Studio Hedgehog+
- JDK 17
- Android SDK 36

### Build
```bash
./gradlew assembleDebug
```

### Test
```bash
./gradlew test
```

## 🎯 Guidelines

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable/function names
- Add KDoc comments for public APIs
- Keep functions focused and concise

### Architecture
- Activities handle UI only
- Business logic in managers/services
- Security checks in `com.dikacode.security` package
- Use `lifecycleScope` for coroutines (no raw `CoroutineScope`)

### Security
- Never hardcode secrets, keys, or credentials
- All security classes must be kept in ProGuard
- Run security tests before submitting PRs
- Document any new security signals

### Commit Messages
- Use conventional commits: `feat:`, `fix:`, `test:`, `docs:`
- Keep subject line under 72 characters
- Reference issues when applicable

## 🧪 Testing

### Run All Tests
```bash
./gradlew test
```

### Run Security Tests Only
```bash
./gradlew test --tests "com.dikacode.security.*"
```

### Test Categories
- `RiskAssessmentTest` — Risk calculation logic
- `SecurityPolicyTest` — Policy response matrix
- `SecurityEventTest` — Event schema validation
- `SecurityTestSuite` — Architecture & integration

## 📝 Pull Request Checklist

- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] No new lint warnings
- [ ] Security tests included (if applicable)
- [ ] Documentation updated (if applicable)
- [ ] Commit messages follow conventions

## ⚠️ Security Contributions

If you find a security vulnerability:

1. **DO NOT** open a public issue
2. Contact [@dikaacode](https://t.me/dikaacode) directly
3. Allow time for assessment before public disclosure

## 📄 License

By contributing, you agree that your contributions will be licensed under the proprietary license.

---

Developed by [@dikaacode](https://t.me/dikaacode)
