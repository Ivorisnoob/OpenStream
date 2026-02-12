# Contributing to OpenAnime

Thank you for considering contributing to OpenAnime. This document outlines the process and guidelines for contributing to the project.

## Code of Conduct

Be respectful and professional in all interactions. We aim to maintain a welcoming environment for all contributors.

## Getting Started

1. Fork the repository
2. Clone your fork locally
3. Create a new branch for your feature or bugfix
4. Make your changes
5. Test thoroughly
6. Submit a pull request

## Development Setup

### Prerequisites
- JDK 17
- Android Studio (latest stable version)
- TMDB API key (add to `local.properties`)

### Building the Project
```bash
./gradlew assembleDebug
```

### Running Tests
```bash
./gradlew test
./gradlew connectedAndroidTest
```

## Development Guidelines

### Architecture
- Follow Clean Architecture principles
- Maintain clear separation between domain, data, and presentation layers
- Use dependency injection via Hilt
- Keep ViewModels focused on state management

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Write self-documenting code with minimal comments
- Keep functions small and focused on a single responsibility

### UI Development
- Use Material Design 3 Expressive components
- Check for Expressive versions before using standard M3 components
- Never create custom components that duplicate M3 functionality
- Follow the component priority rules in `AGENT.md`
- Maintain consistent spacing and layout patterns

### Commit Messages
Use clear, descriptive commit messages:
```
Add search history persistence

- Implement SharedPreferences storage
- Add JSON serialization for search queries
- Update SearchViewModel to load/save history
```

### Pull Request Process

1. **Update Documentation**
   - Update `AGENT.md` if you change architecture or add major features
   - Update README.md if you add user-facing features
   - Add inline code documentation for complex logic

2. **Test Your Changes**
   - Ensure the app builds without errors
   - Test on multiple screen sizes if UI changes
   - Verify no regressions in existing functionality

3. **Keep PRs Focused**
   - One feature or bugfix per PR
   - Avoid mixing refactoring with feature additions
   - Keep changes as small as reasonably possible

4. **PR Description**
   - Clearly describe what the PR does
   - Reference any related issues
   - Include screenshots for UI changes
   - List any breaking changes

### Code Review

All submissions require review. We use GitHub pull requests for this purpose. Reviewers will check for:
- Code quality and adherence to guidelines
- Architecture consistency
- Test coverage
- Documentation updates
- Performance implications

## What to Contribute

### Good First Issues
- UI polish and refinements
- Error message improvements
- Documentation updates
- Test coverage additions

### Feature Requests
Before implementing a new feature:
1. Open an issue to discuss the feature
2. Wait for maintainer feedback
3. Ensure it aligns with project goals
4. Follow the agreed-upon approach

### Bug Reports
When reporting bugs, include:
- Android version and device model
- Steps to reproduce
- Expected vs actual behavior
- Relevant logs or screenshots
- TMDB API response if network-related

### Areas for Contribution

**High Priority:**
- Paging 3 implementation for infinite scroll
- Room database migration for local storage
- Watch history tracking from player
- Error handling and retry mechanisms
- Offline support

**Medium Priority:**
- Subtitle support
- Download functionality
- Advanced search filters
- User preferences and settings
- Performance optimizations

**Low Priority:**
- Additional themes
- Accessibility improvements
- Localization
- Widget support

## Dependency Management

- All versions are managed in `libs.versions.toml`
- Research dependencies before adding new ones
- Verify artifact IDs and versions
- Prefer stable releases over alpha/beta when possible
- Document why a dependency is needed

## Testing Guidelines

### Unit Tests
- Test ViewModels and business logic
- Mock dependencies using appropriate frameworks
- Aim for meaningful test coverage, not just high percentages

### UI Tests
- Test critical user flows
- Use Compose testing APIs
- Keep tests maintainable and readable

## Documentation

### Code Documentation
- Document complex algorithms or business logic
- Explain "why" not "what" in comments
- Keep documentation up-to-date with code changes

### Architecture Documentation
- Update `AGENT.md` for architectural changes
- Document design decisions in commit messages
- Add diagrams for complex flows if helpful

## Questions?

If you have questions about contributing:
- Check existing issues and discussions
- Review `AGENT.md` for codebase context
- Open a discussion for general questions
- Open an issue for specific problems

## Recognition

Contributors will be recognized in release notes and the project README. Significant contributions may result in collaborator status.

---

Thank you for contributing to OpenAnime!
