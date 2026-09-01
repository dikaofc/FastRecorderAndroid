import sys

content = open("app/src/main/AndroidManifest.xml").read()
if 'android:name=".SettingsActivity"' not in content:
    content = content.replace(
        '</application>',
        '    <activity\n            android:name=".SettingsActivity"\n            android:exported="false"\n            android:theme="@style/Theme.MyApplication" />\n    </application>'
    )
    with open("app/src/main/AndroidManifest.xml", "w") as f:
        f.write(content)
