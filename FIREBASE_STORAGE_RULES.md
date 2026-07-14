# Firebase Storage Rules

## For Development (Allow all authenticated users)

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // Allow read/write access to all files for authenticated users
    match /{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## For Production (More Secure)

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // Profile images folder
    match /profile_images/{fileName} {
      // Allow read for all authenticated users
      allow read: if request.auth != null;
      
      // Allow write only if:
      // 1. User is authenticated
      // 2. File is an image (jpeg/png)
      // 3. File size is under 5MB
      allow write: if request.auth != null 
        && request.resource.contentType.matches('image/.*')
        && request.resource.size < 5 * 1024 * 1024;
    }
    
    // Deny access to all other paths by default
    match /{allPaths=**} {
      allow read, write: if false;
    }
  }
}
```

## How to Apply

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your project
3. Go to **Storage** → **Rules**
4. Paste the rules above
5. Click **Publish**

## Important Notes

- The "Object does not exist at location" error usually means:
  1. File path is incorrect
  2. Storage rules don't allow write access
  3. File doesn't exist locally before upload

- Always validate image URI is readable before uploading (included in code fixes)

- Using `putFile()` with `ACTION_OPEN_DOCUMENT` URIs often fails - the code now:
  1. First tries `putFile()` (more efficient)
  2. Falls back to `putBytes()` if putFile fails (works with all content URIs)

## Debugging Tips

Check logs for these tags:
- `AdminAddUser` - User creation flow
- `AdminStudentDetails` - Student profile updates
- `EditProfile` - Profile editing

If upload fails:
1. Check log messages for specific errors
2. Verify Storage Rules in Firebase Console
3. Ensure Firebase Storage is enabled in your project
4. Check that the image file is valid and readable
