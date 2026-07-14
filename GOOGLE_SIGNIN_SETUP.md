# Google Sign-In Setup Guide

## Error 10 (DEVELOPER_ERROR) - How to Fix

Error 10 means your app is not configured in Google Cloud Console. Follow these steps to fix it:

### Step 1: Get Your SHA-1 Fingerprint

#### For Debug Build (Development):
```bash
# Windows (PowerShell)
cd android
.\gradlew signingReport

# Or using keytool directly
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

#### For Release Build:
```bash
keytool -list -v -keystore your-release-key.keystore -alias your-key-alias
```

Look for the line that says `SHA1:` and copy that value.

### Step 2: Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable **Google Sign-In API**:
   - Go to "APIs & Services" > "Library"
   - Search for "Google Sign-In API"
   - Click "Enable"

### Step 3: Configure OAuth 2.0

1. Go to "APIs & Services" > "Credentials"
2. Click "Create Credentials" > "OAuth client ID"
3. If prompted, configure the OAuth consent screen first
4. Select "Android" as application type
5. Enter:
   - **Name**: TrueMark (or your app name)
   - **Package name**: `com.ljku.truemark` (check your `build.gradle.kts` for `applicationId`)
   - **SHA-1 certificate fingerprint**: Paste the SHA-1 you got from Step 1
6. Click "Create"

### Step 4: Get Your Client ID

After creating the OAuth client, you'll get a **Client ID** (looks like: `123456789-abcdefghijklmnop.apps.googleusercontent.com`)

### Step 5: Add Client ID to Your App (Optional)

If you want to use a specific Client ID, you can add it to your `GoogleSignInOptions`:

```kotlin
val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
    .requestEmail()
    .requestProfile()
    .requestIdToken("YOUR_CLIENT_ID_HERE") // Optional: Add your Client ID
    .build()
```

**Note**: For basic sign-in, you don't need to add the Client ID - Google will use the default one based on your package name and SHA-1.

### Step 6: Rebuild and Test

1. Clean and rebuild your project
2. Make sure you're using the same keystore that matches your SHA-1
3. Test Google Sign-In again

## Quick Fix for Development

If you just want to test without full setup:

1. **Use Email Login Instead**: The app supports email/password login which works without any configuration
2. **Get SHA-1 for Debug**: Run `gradlew signingReport` and copy the SHA-1
3. **Add to Google Cloud Console**: Follow steps 2-4 above

## Common Issues

### Issue: "Error 10" persists after setup
- **Solution**: Make sure the SHA-1 fingerprint matches exactly (no spaces, correct case)
- **Solution**: Wait a few minutes after adding SHA-1 - Google needs time to propagate
- **Solution**: Make sure you're using the correct keystore (debug vs release)

### Issue: "Package name mismatch"
- **Solution**: Check that the package name in Google Cloud Console matches your `applicationId` in `build.gradle.kts`

### Issue: "API not enabled"
- **Solution**: Make sure "Google Sign-In API" is enabled in Google Cloud Console

## Alternative: Use Email Login

If Google Sign-In setup is too complex, users can always use the email/password login which works immediately without any configuration.

