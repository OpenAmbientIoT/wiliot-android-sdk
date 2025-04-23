#!/bin/bash

# Input Files
CHANGED_MODULES_FILE="changed_sdk_modules.txt"  # File with changed modules
VERSION_CONTROL_FILE="version-control.yml"     # File defining increment types
SDK_VERSIONS_FILE="sdk-versions.gradle"        # File containing versions

# Function to increment a version based on the increment type
increment_version() {
    local current_version="$1"
    local increment_type="$2"

    local major=$(echo "$current_version" | cut -d. -f1)
    local minor=$(echo "$current_version" | cut -d. -f2)
    local patch=$(echo "$current_version" | cut -d. -f3)

    case $increment_type in
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        patch)
            patch=$((patch + 1))
            ;;
        *)
            echo "Error: Unknown increment type '$increment_type'" >&2
            exit 1
            ;;
    esac

    echo "$major.$minor.$patch"
}

# Validate files
if [[ ! -f "$CHANGED_MODULES_FILE" || ! -f "$VERSION_CONTROL_FILE" || ! -f "$SDK_VERSIONS_FILE" ]]; then
    echo "Error: One or more required files are missing." >&2
    exit 1
fi

# Read changed modules
CHANGED_MODULES=($(cat "$CHANGED_MODULES_FILE"))
if [[ ${#CHANGED_MODULES[@]} -eq 0 ]]; then
    echo "No changed modules detected. Skipping version updates."
    exit 0
fi

# Initialize the highest priority increment type
highest_increment="patch"

# Iterate through each changed module
for MODULE in "${CHANGED_MODULES[@]}"; do
    # Extract increment type for the module
    INCREMENT_TYPE=$(grep "$MODULE:" "$VERSION_CONTROL_FILE" | awk '{print $2}')
    if [[ -z "$INCREMENT_TYPE" ]]; then
        echo "Warning: No increment type found for $MODULE. Skipping."
        continue
    fi

    # Map module name to version key in sdk-versions.gradle
    VERSION_KEY=$(echo "$MODULE" | sed 's/wiliot-//g' | sed -r 's/(^|-)([a-z])/\U\2/g')VersionName

    # Extract the current version
    CURRENT_VERSION=$(grep "$VERSION_KEY" "$SDK_VERSIONS_FILE" | awk -F'"' '{print $2}')
    if [[ -z "$CURRENT_VERSION" ]]; then
        echo "Warning: No version found for $MODULE ($VERSION_KEY). Skipping."
        continue
    fi

    # Increment the version
    NEW_VERSION=$(increment_version "$CURRENT_VERSION" "$INCREMENT_TYPE")
    echo "Updating $MODULE ($VERSION_KEY): $CURRENT_VERSION -> $NEW_VERSION"

    # Update the version in sdk-versions.gradle
    sed -i.bak "s/$VERSION_KEY = \"$CURRENT_VERSION\"/$VERSION_KEY = \"$NEW_VERSION\"/" "$SDK_VERSIONS_FILE"

    # Determine the highest priority increment type
    case $INCREMENT_TYPE in
        major)
            highest_increment="major"
            break
            ;;
        minor)
            [[ "$highest_increment" != "major" ]] && highest_increment="minor"
            ;;
    esac
done

# Update BOM version based on the highest increment type
BOM_VERSION=$(grep "bomVersionName" "$SDK_VERSIONS_FILE" | awk -F'"' '{print $2}')
if [[ -z "$BOM_VERSION" ]]; then
    echo "Error: Failed to read BOM version from $SDK_VERSIONS_FILE" >&2
    exit 1
fi

NEW_BOM_VERSION=$(increment_version "$BOM_VERSION" "$highest_increment")
echo "Updating BOM version: $BOM_VERSION -> $NEW_BOM_VERSION"

# Update the BOM version in sdk-versions.gradle
sed -i.bak "s/bomVersionName = \"$BOM_VERSION\"/bomVersionName = \"$NEW_BOM_VERSION\"/" "$SDK_VERSIONS_FILE"

# Confirm the BOM update
if grep -q "bomVersionName = \"$NEW_BOM_VERSION\"" "$SDK_VERSIONS_FILE"; then
    echo "✅ BOM version updated successfully to $NEW_BOM_VERSION"
else
    echo "❌ Failed to update BOM version." >&2
    exit 1
fi

echo "Version updates completed successfully."
