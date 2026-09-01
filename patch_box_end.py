import sys
content = open("app/src/main/java/com/example/MainActivity.kt").read()

end_home_idx = content.find("        }\n    }\n}\n\n@Composable\nfun SettingsScreen")
if end_home_idx != -1:
    new_end = "        }\n    }\n    }\n}\n\n@Composable\nfun SettingsScreen"
    content = content.replace("        }\n    }\n}\n\n@Composable\nfun SettingsScreen", new_end)
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(content)
        print("Success End Box")
else:
    print("Not found")

