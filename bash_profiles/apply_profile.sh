#!/bin/bash

set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <original_bash_profile> <custom_bash_profile>"
    exit 1
fi

original_bash_profile="$1"
custom_bash_profile="$2"

# if the original profile is a link to the custom file
if [ "$original_bash_profile" -ef "$custom_bash_profile" ]; then
    echo "Already applied: $original_bash_profile is the same file as $custom_bash_profile"
    exit 0
fi

echo >> "$original_bash_profile"
cat "$custom_bash_profile" >> "$original_bash_profile"

mv -fv "$original_bash_profile" "$custom_bash_profile"
ln -fsv "$custom_bash_profile" "$original_bash_profile"