[Русский](README.md) | English

# ShizuFreeze

ShizuFreeze is a Shizuku-only fork of Hail for freezing Android apps.

## Scope

- Works only through Shizuku
- Root / Device Owner / Dhizuku / Island modes are removed
- Only English and Russian localizations are kept
- Author groups, donation links, and non-essential disclaimers are removed

## API

Use intents with the app package:

- `${applicationId}.action.LAUNCH`
- `${applicationId}.action.FREEZE`
- `${applicationId}.action.UNFREEZE`
- `${applicationId}.action.FREEZE_TAG`
- `${applicationId}.action.UNFREEZE_TAG`
- `${applicationId}.action.FREEZE_ALL`
- `${applicationId}.action.UNFREEZE_ALL`
- `${applicationId}.action.FREEZE_NON_WHITELISTED`
- `${applicationId}.action.FREEZE_AUTO`
- `${applicationId}.action.LOCK`
- `${applicationId}.action.LOCK_FREEZE`
