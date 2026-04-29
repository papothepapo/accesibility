package com.exalt.accessibilityswitcher.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.exalt.accessibilityswitcher.data.RuleStore
import com.exalt.accessibilityswitcher.model.ManagedRule
import com.exalt.accessibilityswitcher.util.MonitorServiceController
import com.exalt.accessibilityswitcher.util.PermissionStatus

class MainActivity : Activity() {
    private lateinit var store: RuleStore
    private lateinit var ruleAdapter: RuleAdapter
    private lateinit var ruleList: ListView
    private lateinit var statusText: TextView
    private lateinit var automationButton: Button
    private lateinit var holdButton: Button
    private lateinit var editButton: Button
    private lateinit var toggleRuleButton: Button
    private lateinit var deleteButton: Button
    private lateinit var upButton: Button
    private lateinit var downButton: Button

    private var rules: List<ManagedRule> = emptyList()
    private var selectedIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RuleStore(this)
        buildLayout()
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        val title = TextView(this).apply {
            text = "Accessibility Switcher"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        root.addView(title, LinearLayout.LayoutParams(matchParent(), wrapContent()))

        statusText = TextView(this).apply {
            setTextColor(COLOR_MUTED)
            textSize = 13f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            setPadding(0, dp(4), 0, dp(6))
        }
        root.addView(statusText, LinearLayout.LayoutParams(matchParent(), wrapContent()))

        val utilityRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        automationButton = createButton("") { toggleAutomation() }
        holdButton = createButton("") { toggleHold() }
        val permissionsButton = createButton("Perms") { showPermissions() }
        val diagnosticsButton = createButton("Diag") { showDiagnostics() }
        utilityRow.addView(automationButton, rowButtonParams())
        utilityRow.addView(holdButton, rowButtonParams())
        utilityRow.addView(permissionsButton, rowButtonParams())
        utilityRow.addView(diagnosticsButton, rowButtonParams())
        root.addView(utilityRow, LinearLayout.LayoutParams(matchParent(), wrapContent()))

        ruleAdapter = RuleAdapter()
        ruleList = ListView(this).apply {
            adapter = ruleAdapter
            choiceMode = ListView.CHOICE_MODE_SINGLE
            setBackgroundColor(COLOR_PANEL)
            divider = null
            isFocusable = true
            isFocusableInTouchMode = false
            setPadding(0, dp(6), 0, dp(6))
            clipToPadding = false
            setOnItemClickListener { _, _, position, _ ->
                selectedIndex = position
                editSelectedRule()
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    selectedIndex = position
                    ruleAdapter.notifyDataSetChanged()
                    updateRuleButtons()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        root.addView(ruleList, LinearLayout.LayoutParams(matchParent(), 0, 1f).apply {
            topMargin = dp(6)
            bottomMargin = dp(6)
        })

        val firstActionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val secondActionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val addButton = createButton("Add") { showRuleEditor(null) }
        editButton = createButton("Edit") { editSelectedRule() }
        toggleRuleButton = createButton("") { toggleSelectedRule() }
        deleteButton = createButton("Delete") { deleteSelectedRule() }
        upButton = createButton("Up") { moveSelectedRule(-1) }
        downButton = createButton("Down") { moveSelectedRule(1) }

        firstActionRow.addView(addButton, rowButtonParams())
        firstActionRow.addView(editButton, rowButtonParams())
        firstActionRow.addView(toggleRuleButton, rowButtonParams())
        secondActionRow.addView(upButton, rowButtonParams())
        secondActionRow.addView(downButton, rowButtonParams())
        secondActionRow.addView(deleteButton, rowButtonParams())
        root.addView(firstActionRow, LinearLayout.LayoutParams(matchParent(), wrapContent()))
        root.addView(secondActionRow, LinearLayout.LayoutParams(matchParent(), wrapContent()).apply {
            topMargin = dp(4)
        })

        setContentView(root)
        automationButton.requestFocus()
    }

    private fun refreshState() {
        rules = store.getRules()
        selectedIndex = when {
            rules.isEmpty() -> -1
            selectedIndex !in rules.indices -> 0
            else -> selectedIndex
        }
        ruleAdapter.setRules(rules)
        if (selectedIndex >= 0) {
            ruleList.setSelection(selectedIndex)
            ruleList.setItemChecked(selectedIndex, true)
        }

        automationButton.text = if (store.isAutomationEnabled()) "Auto On" else "Auto Off"
        holdButton.text = if (store.isHoldEnabled()) "Hold On" else "Hold Off"
        statusText.text = statusSummary()
        updateRuleButtons()
    }

    private fun updateRuleButtons() {
        val hasSelection = selectedIndex in rules.indices
        editButton.isEnabled = hasSelection
        toggleRuleButton.isEnabled = hasSelection
        deleteButton.isEnabled = hasSelection
        upButton.isEnabled = hasSelection && selectedIndex > 0
        downButton.isEnabled = hasSelection && selectedIndex >= 0 && selectedIndex < rules.lastIndex
        toggleRuleButton.text = if (hasSelection && rules[selectedIndex].enabled) {
            "Disable"
        } else {
            "Enable"
        }
    }

    private fun statusSummary(): String {
        val usage = if (PermissionStatus.hasUsageAccess(this)) "Usage OK" else "Usage missing"
        val secure = if (PermissionStatus.hasWriteSecureSettings(this)) "Secure OK" else "Secure missing"
        val mode = when {
            !store.isAutomationEnabled() -> "Automation stopped"
            store.isHoldEnabled() -> "Holding current service"
            else -> "Automation running"
        }
        val lastPackage = store.getLastActivePackage().ifBlank { "none" }
        val lastService = store.getLastAppliedService().ifBlank { "none" }
        val lastError = store.getLastError().ifBlank { "No diagnostics yet" }
        return "$mode | $usage | $secure\nApp: $lastPackage | Service: ${shortComponent(lastService)} | $lastError"
    }

    private fun toggleAutomation() {
        val enabled = !store.isAutomationEnabled()
        store.setAutomationEnabled(enabled)
        if (enabled) {
            MonitorServiceController.start(this)
        } else {
            MonitorServiceController.stop(this)
        }
        refreshState()
    }

    private fun toggleHold() {
        store.setHoldEnabled(!store.isHoldEnabled())
        if (store.isAutomationEnabled()) {
            MonitorServiceController.start(this)
        }
        refreshState()
    }

    private fun editSelectedRule() {
        if (selectedIndex !in rules.indices) {
            toast("Select a rule first")
            return
        }
        showRuleEditor(rules[selectedIndex])
    }

    private fun toggleSelectedRule() {
        if (selectedIndex !in rules.indices) return
        val updated = rules.toMutableList()
        val selected = updated[selectedIndex]
        updated[selectedIndex] = selected.copy(enabled = !selected.enabled)
        store.saveRules(updated)
        restartMonitorIfAutomationEnabled()
        refreshState()
    }

    private fun deleteSelectedRule() {
        if (selectedIndex !in rules.indices) return
        val rule = rules[selectedIndex]
        AlertDialog.Builder(this)
            .setTitle("Delete rule")
            .setMessage("Remove ${rule.packageName}?")
            .setPositiveButton("Delete") { _, _ ->
                val updated = rules.toMutableList()
                updated.removeAt(selectedIndex)
                store.saveRules(updated)
                restartMonitorIfAutomationEnabled()
                selectedIndex = selectedIndex.coerceAtMost(updated.lastIndex)
                refreshState()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun moveSelectedRule(delta: Int) {
        if (selectedIndex !in rules.indices) return
        val target = selectedIndex + delta
        if (target !in rules.indices) return

        val updated = rules.toMutableList()
        val moving = updated.removeAt(selectedIndex)
        updated.add(target, moving)
        selectedIndex = target
        store.saveRules(updated)
        restartMonitorIfAutomationEnabled()
        refreshState()
    }

    private fun showRuleEditor(existing: ManagedRule?) {
        var chosenPackage = existing?.packageName.orEmpty()
        var chosenService = existing?.serviceComponent.orEmpty()
        var chosenEnabled = existing?.enabled ?: true

        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
        }
        val content = ScrollView(this).apply {
            isFillViewport = false
            addView(fields)
        }

        val packageButton = createButton("")
        val serviceButton = createButton("")
        val enabledBox = CheckBox(this).apply {
            text = "Rule enabled"
            textSize = 18f
            setTextColor(Color.WHITE)
            isChecked = chosenEnabled
            buttonTintList = null
            setOnCheckedChangeListener { _, checked -> chosenEnabled = checked }
        }
        val saveButton = createButton("Save")

        fun updateLabels() {
            packageButton.text = if (chosenPackage.isBlank()) {
                "Choose App"
            } else {
                "App: ${labelForPackage(chosenPackage)}"
            }
            serviceButton.text = if (chosenService.isBlank()) {
                "Choose Service"
            } else {
                "Service: ${shortComponent(chosenService)}"
            }
            saveButton.isEnabled = chosenPackage.isNotBlank() && chosenService.isNotBlank()
        }

        packageButton.setOnClickListener {
            showPackagePicker { choice ->
                chosenPackage = choice.packageName
                updateLabels()
            }
        }
        serviceButton.setOnClickListener {
            showServicePicker { choice ->
                chosenService = choice.component
                updateLabels()
            }
        }

        fields.addView(packageButton, dialogButtonParams())
        fields.addView(serviceButton, dialogButtonParams())
        fields.addView(enabledBox, LinearLayout.LayoutParams(matchParent(), wrapContent()).apply {
            topMargin = dp(10)
            bottomMargin = dp(10)
        })
        fields.addView(saveButton, dialogButtonParams())

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add rule" else "Edit rule")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .create()

        saveButton.setOnClickListener {
            if (chosenPackage.isBlank() || chosenService.isBlank()) {
                toast("Choose an app and service")
                return@setOnClickListener
            }

            val updated = rules.toMutableList()
            val savedRule = existing?.copy(
                packageName = chosenPackage,
                serviceComponent = chosenService,
                enabled = chosenEnabled
            ) ?: store.newRule(chosenPackage, chosenService, chosenEnabled)

            if (existing == null) {
                updated += savedRule
                selectedIndex = updated.lastIndex
            } else {
                val index = updated.indexOfFirst { it.id == existing.id }
                if (index >= 0) {
                    updated[index] = savedRule
                    selectedIndex = index
                }
            }
            store.saveRules(updated)
            restartMonitorIfAutomationEnabled()
            dialog.dismiss()
            refreshState()
        }

        updateLabels()
        dialog.setOnShowListener {
            packageButton.requestFocus()
        }
        dialog.show()
    }

    private fun showPackagePicker(onSelected: (PackageChoice) -> Unit) {
        val choices = packageManager
            .getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .map { app ->
                PackageChoice(
                    label = app.loadLabel(packageManager)?.toString().orEmpty().ifBlank { app.packageName },
                    packageName = app.packageName,
                    isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedWith(compareBy<PackageChoice> { it.isSystem }.thenBy { it.label.lowercase() })

        if (choices.isEmpty()) {
            toast("No installed apps found")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Choose app")
            .setAdapter(pickerAdapter(choices.map { "${it.label}\n${it.packageName}" })) { _, which ->
                onSelected(choices[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showServicePicker(onSelected: (ServiceChoice) -> Unit) {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val choices = manager.installedAccessibilityServiceList
            .mapNotNull { serviceInfo -> serviceInfo.toChoice() }
            .sortedBy { it.label.lowercase() }

        if (choices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No services found")
                .setMessage("Install at least one accessibility service, then return here.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Choose service")
            .setAdapter(pickerAdapter(choices.map { "${it.label}\n${it.component}" })) { _, which ->
                onSelected(choices[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun AccessibilityServiceInfo.toChoice(): ServiceChoice? {
        val resolveInfo = resolveInfo ?: return null
        val serviceInfo = resolveInfo.serviceInfo ?: return null
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name).flattenToString()
        val label = resolveInfo.loadLabel(packageManager)?.toString().orEmpty().ifBlank { component }
        return ServiceChoice(label = label, component = component)
    }

    private fun pickerAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.textSize = 16f
                view.setPadding(dp(12), dp(10), dp(12), dp(10))
                view.maxLines = 3
                return view
            }
        }
    }

    private fun showPermissions() {
        val message = buildString {
            append("WRITE_SECURE_SETTINGS\n")
            append(PermissionStatus.grantCommand(this@MainActivity))
            append("\n\nUsage Access must be enabled for this app in Android settings.")
        }

        AlertDialog.Builder(this)
            .setTitle("Required permissions")
            .setMessage(message)
            .setPositiveButton("Usage Access") { _, _ ->
                startActivity(PermissionStatus.usageAccessIntent())
            }
            .setNeutralButton("Accessibility") { _, _ ->
                startActivity(PermissionStatus.accessibilitySettingsIntent())
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDiagnostics() {
        val secureList = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty()
                .ifBlank { "(empty)" }
        } catch (error: RuntimeException) {
            "Unreadable: ${error.message.orEmpty()}"
        }
        val accessibilityEnabled = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
                .orEmpty()
                .ifBlank { "(empty)" }
        } catch (error: RuntimeException) {
            "Unreadable: ${error.message.orEmpty()}"
        }

        val message = buildString {
            append("Automation: ${store.isAutomationEnabled()}\n")
            append("Hold current: ${store.isHoldEnabled()}\n")
            append("Rules: ${rules.size}\n")
            append("Usage Access: ${PermissionStatus.hasUsageAccess(this@MainActivity)}\n")
            append("WRITE_SECURE_SETTINGS: ${PermissionStatus.hasWriteSecureSettings(this@MainActivity)}\n")
            append("Last app: ${store.getLastActivePackage().ifBlank { "(none)" }}\n")
            append("Last applied: ${store.getLastAppliedService().ifBlank { "(none)" }}\n")
            append("Secure list: $secureList\n")
            append("Accessibility enabled: $accessibilityEnabled\n")
            append("Last diagnostic: ${store.getLastError().ifBlank { "(none)" }}")
        }

        AlertDialog.Builder(this)
            .setTitle("Diagnostics")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun labelForPackage(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = appInfo.loadLabel(packageManager)?.toString().orEmpty()
            if (label.isBlank()) packageName else "$label ($packageName)"
        } catch (_: Exception) {
            packageName
        }
    }

    private fun shortComponent(component: String): String {
        val flattened = ComponentName.unflattenFromString(component)
        return flattened?.let { "${it.packageName}/${it.shortClassName}" } ?: component
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun restartMonitorIfAutomationEnabled() {
        if (store.isAutomationEnabled()) {
            MonitorServiceController.start(this)
        }
    }

    private fun createButton(textValue: String, onClick: (() -> Unit)? = null): Button {
        return Button(this).apply {
            text = textValue
            isAllCaps = false
            textSize = 13f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            minHeight = dp(40)
            minimumHeight = dp(40)
            minWidth = 0
            minimumWidth = 0
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = focusBackground(COLOR_BUTTON, COLOR_FOCUS, COLOR_DISABLED)
            onClick?.let { click -> setOnClickListener { click() } }
        }
    }

    private fun rowButtonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            leftMargin = dp(2)
            rightMargin = dp(2)
        }
    }

    private fun dialogButtonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(matchParent(), dp(50)).apply {
            topMargin = dp(6)
            bottomMargin = dp(6)
        }
    }

    private fun focusBackground(normalColor: Int, focusedColor: Int, disabledColor: Int): StateListDrawable {
        return StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), rounded(disabledColor))
            addState(intArrayOf(android.R.attr.state_pressed), rounded(focusedColor))
            addState(intArrayOf(android.R.attr.state_focused), rounded(focusedColor))
            addState(intArrayOf(), rounded(normalColor))
        }
    }

    private fun rounded(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(6).toFloat()
        }
    }

    private fun selectedRuleBackground(): GradientDrawable {
        return rounded(COLOR_SELECTED)
    }

    private fun normalRuleBackground(): GradientDrawable {
        return rounded(COLOR_PANEL)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun matchParent(): Int = ViewGroup.LayoutParams.MATCH_PARENT

    private fun wrapContent(): Int = ViewGroup.LayoutParams.WRAP_CONTENT

    private data class PackageChoice(
        val label: String,
        val packageName: String,
        val isSystem: Boolean
    )

    private data class ServiceChoice(
        val label: String,
        val component: String
    )

    private inner class RuleAdapter : BaseAdapter() {
        private var data: List<ManagedRule> = emptyList()

        fun setRules(rules: List<ManagedRule>) {
            data = rules
            notifyDataSetChanged()
        }

        override fun getCount(): Int = data.size

        override fun getItem(position: Int): ManagedRule = data[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val textView = (convertView as? TextView) ?: TextView(this@MainActivity).apply {
                textSize = 14f
                minHeight = dp(48)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
                setPadding(dp(8), dp(6), dp(8), dp(6))
            }
            val rule = data[position]
            val enabled = if (rule.enabled) "ON" else "OFF"
            textView.text = "${position + 1}. $enabled ${rule.packageName}\n${shortComponent(rule.serviceComponent)}"
            textView.setTextColor(if (rule.enabled) Color.WHITE else COLOR_MUTED)
            textView.background = if (position == selectedIndex) {
                selectedRuleBackground()
            } else {
                normalRuleBackground()
            }
            return textView
        }
    }

    private companion object {
        val COLOR_BACKGROUND: Int = Color.rgb(17, 24, 39)
        val COLOR_PANEL: Int = Color.rgb(31, 41, 55)
        val COLOR_BUTTON: Int = Color.rgb(15, 118, 110)
        val COLOR_FOCUS: Int = Color.rgb(234, 179, 8)
        val COLOR_SELECTED: Int = Color.rgb(55, 65, 81)
        val COLOR_DISABLED: Int = Color.rgb(75, 85, 99)
        val COLOR_MUTED: Int = Color.rgb(209, 213, 219)
    }
}
