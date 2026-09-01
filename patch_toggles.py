import sys

content = open("app/src/main/java/com/example/SettingsActivity.kt").read()

bad = """    private fun updateToggle(textView: android.widget.TextView, isOn: Boolean) {
        if (isOn) {
            textView.text = "ON"
            textView.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            textView.setBackgroundResource(R.drawable.bg_neo_toggle_on) // Use the red brutalist circle or create a solid block
        } else {
            textView.text = "OFF"
            textView.setTextColor(android.graphics.Color.parseColor("#0A0A0A"))
            textView.setBackgroundResource(R.drawable.bg_neo_button)
        }
    }"""

good = """    private fun updateToggle(textView: android.widget.TextView, isOn: Boolean) {
        if (isOn) {
            textView.text = "ON"
            textView.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            textView.setBackgroundResource(R.drawable.bg_neo_toggle_on)
        } else {
            textView.text = "OFF"
            textView.setTextColor(android.graphics.Color.parseColor("#0A0A0A"))
            textView.setBackgroundResource(R.drawable.bg_neo_toggle_off)
        }
    }"""

content = content.replace(bad, good)

with open("app/src/main/java/com/example/SettingsActivity.kt", "w") as f:
    f.write(content)
