import sys
content = open("app/build.gradle.kts").read()

deps = """
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
"""

content = content.replace("dependencies {", "dependencies {" + deps)

with open("app/build.gradle.kts", "w") as f:
    f.write(content)
