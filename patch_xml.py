import sys

content = open("app/src/main/res/layout/activity_settings.xml").read()

old_tv = """            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="/sdcard/ZetaRecorder/"
                android:textColor="#333333"
                android:textStyle="bold"
                android:layout_marginTop="4dp"
                android:layout_marginBottom="8dp"/>"""

new_tv = """            <TextView
                android:id="@+id/tvStoragePath"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="/sdcard/Movies/FastRecorder/"
                android:textColor="@color/neo_black"
                android:textStyle="bold"
                android:padding="12dp"
                android:background="@drawable/bg_neo_button"
                android:layout_marginTop="8dp"
                android:layout_marginBottom="8dp"/>"""

content = content.replace(old_tv, new_tv)

with open("app/src/main/res/layout/activity_settings.xml", "w") as f:
    f.write(content)

