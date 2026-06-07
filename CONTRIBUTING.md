# Contributing to Bushers Flasher Mobile

We welcome contributions to the Bushers Flasher Mobile application! By participating in this project, you agree to abide by our code of conduct.

## Development Workflow: Trunk Based Development (tbdflow)

This project strictly follows **tbdflow** (Trunk Based Development).
1. **No long-lived feature branches**: All development happens in short-lived branches (living less than a couple of days).
2. **Branch Naming**: Prefix branches with your initials or a short identifier, and a ticket/issue number if applicable (e.g., `gb/123-add-scan-button`).
3. **Merge frequently**: Integrate your work into the `main` trunk as frequently as possible via Pull Requests.
4. **Rebase, don't merge**: Always rebase your short-lived branch on top of `main` before submitting a Pull Request to keep the history linear.

## Commit Message Guidelines

We use **Conventional Commits** to auto-generate changelogs and maintain a readable history. 
**CRITICAL: The first line (header) MUST NOT exceed 50 characters.**

### Format:
```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Types:
*   `feat`: A new feature
*   `fix`: A bug fix
*   `docs`: Documentation only changes
*   `style`: Changes that do not affect the meaning of the code (white-space, formatting, etc)
*   `refactor`: A code change that neither fixes a bug nor adds a feature
*   `perf`: A code change that improves performance
*   `test`: Adding missing tests or correcting existing tests
*   `chore`: Changes to the build process or auxiliary tools and libraries

**Example (Valid - under 50 chars):**
```
feat(ui): add scan button
```

**Example (Invalid - over 50 chars):**
```
feat(ui): add a scan button to the devices screen so the user can scan for new devices
```

## Coding Standards

*   **Kotlin**: Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
*   **UI**: Use **Jetpack Compose** exclusively for new UI components. Follow Material 3 design guidelines and leverage the predefined theme tokens in `Theme.kt` and `Color.kt`.
*   **Architecture**: Maintain the MVVM architecture. Keep UI components stateless where possible, passing events up to the ViewModel.
*   **Linting**: Before opening a PR, ensure that your code passes standard Android lint checks:
    ```bash
    ./gradlew lintDebug
    ```
