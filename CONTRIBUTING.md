# Contributing to Bushers Flasher Mobile

We welcome contributions to the Bushers Flasher Mobile application! By participating in this project, you agree to abide by our code of conduct.

## How to Contribute

1.  **Fork the repository** on GitHub.
2.  **Create a new branch** for your feature or bug fix (`git checkout -b feature/your-feature-name`).
3.  **Write your code**, ensuring you follow the project's coding standards.
4.  **Test your changes** thoroughly.
5.  **Commit your changes** with descriptive commit messages (`git commit -m 'Add some feature'`).
6.  **Push to your branch** (`git push origin feature/your-feature-name`).
7.  **Create a Pull Request** against the `main` branch of this repository.

## Coding Standards

*   **Kotlin**: Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
*   **UI**: Use **Jetpack Compose** exclusively for new UI components. Follow Material 3 design guidelines and leverage the predefined theme tokens in `Theme.kt` and `Color.kt`.
*   **Architecture**: Maintain the MVVM architecture. Keep UI components stateless where possible, passing events up to the ViewModel.
*   **Linting**: Before opening a PR, ensure that your code passes standard Android lint checks:
    ```bash
    ./gradlew lintDebug
    ```

## Bug Reports and Feature Requests

Please use the GitHub Issue Tracker to report bugs or request new features. When reporting a bug, include:
*   Your device model and Android version.
*   Steps to reproduce the issue.
*   Expected behavior vs. actual behavior.
*   Screenshots or logs if applicable.
