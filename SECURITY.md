# Security policy

This is a personal project that reads the owner's Hevy data (read-only) and stores it in a private database.

## Reporting a vulnerability
Please **do not open a public issue**. Use GitHub's private vulnerability reporting:
[Report a vulnerability](https://github.com/NoFortunateSon/app-hevy-insights/security/advisories/new).
You should get an answer within a few days.

## Secrets
No secret is stored in this repository. The Hevy API key and database credentials live only in GitHub Actions
secrets and in local, git-ignored `.env` files. If you ever find one in the code or history, report it as above.
