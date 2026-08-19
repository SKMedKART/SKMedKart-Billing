SKMedKART - Build APK from phone using GitHub Actions

1. Create a new GitHub repository named SKMedKART.
2. Upload ALL files/folders in this project to the repository root.
3. Make sure .github/workflows/build-apk.yml is uploaded too.
4. Open the repository -> Actions -> Build SKMedKART APK.
5. Tap Run workflow.
6. Wait for the green check.
7. Open the completed workflow run -> Artifacts -> SKMedKART-debug-apk.
8. Download the ZIP artifact and extract app-debug.apk.
9. Install the APK on the Android phone.

Important: Do NOT upload this whole project ZIP as the only repository file. GitHub Actions must see the project folders/files and .github/workflows/build-apk.yml in the repository.
