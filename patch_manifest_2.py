import sys
content = open("app/src/main/AndroidManifest.xml").read()

if 'xmlns:tools="http://schemas.android.com/tools"' not in content:
    content = content.replace("<manifest ", '<manifest xmlns:tools="http://schemas.android.com/tools" ')

content = content.replace("<uses-permission ", '<uses-sdk tools:overrideLibrary="androidx.core.ktx,androidx.core,androidx.activity,androidx.lifecycle,androidx.lifecycle.runtime,androidx.lifecycle.viewmodel,androidx.lifecycle.livedata,androidx.savedstate,androidx.fragment,androidx.loader,androidx.versionedparcelable,androidx.annotation,androidx.collection,androidx.arch.core,androidx.customview,androidx.viewpager,androidx.coordinatorlayout,androidx.drawerlayout,androidx.slidingpanelayout,androidx.interpolator,androidx.swiperefreshlayout,androidx.asynclayoutinflater,androidx.cursoradapter,androidx.print,androidx.documentfile,androidx.localbroadcastmanager" />\n    <uses-permission ', 1)

with open("app/src/main/AndroidManifest.xml", "w") as f:
    f.write(content)
