import sys
content = open("app/build.gradle.kts").read()

content = content.replace("minSdk = 29", "minSdk = 21")
content = content.replace("targetSdk = 36", "targetSdk = 34")

# Add viewBinding
if "viewBinding = true" not in content:
    content = content.replace("buildFeatures {\n    compose = true", "buildFeatures {\n    viewBinding = true\n    compose = true")

# Shrink resources and minify in release
release_block = """    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }"""
content = content.replace("""    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }""", release_block)

with open("app/build.gradle.kts", "w") as f:
    f.write(content)
