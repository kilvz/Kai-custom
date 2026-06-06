package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_get_battery_info_description
import kai.composeapp.generated.resources.tool_get_battery_info_name
import kai.composeapp.generated.resources.tool_get_device_info_description
import kai.composeapp.generated.resources.tool_get_device_info_name
import kai.composeapp.generated.resources.tool_get_gps_location_description
import kai.composeapp.generated.resources.tool_get_gps_location_name
import kai.composeapp.generated.resources.tool_get_network_info_description
import kai.composeapp.generated.resources.tool_get_network_info_name
import kai.composeapp.generated.resources.tool_get_wifi_info_description
import kai.composeapp.generated.resources.tool_get_wifi_info_name
import kai.composeapp.generated.resources.tool_list_installed_apps_description
import kai.composeapp.generated.resources.tool_list_installed_apps_name
import kai.composeapp.generated.resources.tool_read_clipboard_description
import kai.composeapp.generated.resources.tool_read_clipboard_name
import kai.composeapp.generated.resources.tool_read_contacts_description
import kai.composeapp.generated.resources.tool_read_contacts_name

object PhoneTools {

    val gpsLocationToolInfo = ToolInfo(
        id = "get_gps_location",
        name = "Get GPS Location",
        description = "Get precise GPS location coordinates (latitude, longitude, accuracy)",
        nameRes = Res.string.tool_get_gps_location_name,
        descriptionRes = Res.string.tool_get_gps_location_description,
    )

    val readContactsToolInfo = ToolInfo(
        id = "read_contacts",
        name = "Read Contacts",
        description = "Search and read contacts from the device phonebook",
        nameRes = Res.string.tool_read_contacts_name,
        descriptionRes = Res.string.tool_read_contacts_description,
    )

    val deviceInfoToolInfo = ToolInfo(
        id = "get_device_info",
        name = "Get Device Info",
        description = "Get detailed device information including model, Android version, and hardware specs",
        nameRes = Res.string.tool_get_device_info_name,
        descriptionRes = Res.string.tool_get_device_info_description,
    )

    val batteryInfoToolInfo = ToolInfo(
        id = "get_battery_info",
        name = "Get Battery Info",
        description = "Get battery level, charging status, and temperature",
        nameRes = Res.string.tool_get_battery_info_name,
        descriptionRes = Res.string.tool_get_battery_info_description,
    )

    val networkInfoToolInfo = ToolInfo(
        id = "get_network_info",
        name = "Get Network Info",
        description = "Get current network connectivity details (WiFi/cellular status, IP, signal strength)",
        nameRes = Res.string.tool_get_network_info_name,
        descriptionRes = Res.string.tool_get_network_info_description,
    )

    val wifiInfoToolInfo = ToolInfo(
        id = "get_wifi_info",
        name = "Get WiFi Info",
        description = "Get detailed WiFi connection information including SSID, signal strength, and frequency",
        nameRes = Res.string.tool_get_wifi_info_name,
        descriptionRes = Res.string.tool_get_wifi_info_description,
    )

    val clipboardToolInfo = ToolInfo(
        id = "read_clipboard",
        name = "Read Clipboard",
        description = "Read the current content of the system clipboard",
        nameRes = Res.string.tool_read_clipboard_name,
        descriptionRes = Res.string.tool_read_clipboard_description,
    )

    val installedAppsToolInfo = ToolInfo(
        id = "list_installed_apps",
        name = "List Installed Apps",
        description = "List all installed applications on the device",
        nameRes = Res.string.tool_list_installed_apps_name,
        descriptionRes = Res.string.tool_list_installed_apps_description,
    )

    // ── Missing permission tools ──

    val readCalendarToolInfo = ToolInfo(
        id = "read_calendar_events",
        name = "Read Calendar Events",
        description = "Read calendar events from the device calendar",
    )

    val writeContactToolInfo = ToolInfo(
        id = "write_contact",
        name = "Write Contact",
        description = "Create or update a contact in the device phonebook",
    )

    val getPhoneStateToolInfo = ToolInfo(
        id = "get_phone_state",
        name = "Get Phone State",
        description = "Get cellular network info, signal strength, operator name, device ID",
    )

    val scanBluetoothToolInfo = ToolInfo(
        id = "scan_bluetooth_devices",
        name = "Scan Bluetooth Devices",
        description = "Scan for nearby Bluetooth devices and show paired devices",
    )

    val listMediaToolInfo = ToolInfo(
        id = "list_media",
        name = "List Media Files",
        description = "List images, videos, and audio files on the device",
    )

    val readLogsToolInfo = ToolInfo(
        id = "read_device_logs",
        name = "Read Device Logs",
        description = "Read recent system logs (logcat)",
    )

    val setGpsLocationToolInfo = ToolInfo(
        id = "set_gps_location",
        name = "Set GPS Location",
        description = "Set a mock GPS location on the device. Opens mock location settings if not configured.",
    )

    val takePictureToolInfo = ToolInfo(
        id = "take_picture",
        name = "Take Picture",
        description = "Capture a photo using the device camera (front or back). Saves to AI storage and returns the image for vision-capable models.",
    )

    val hearSurroundingsToolInfo = ToolInfo(
        id = "hear_surroundings",
        name = "Hear Surroundings",
        description = "Listen through the device microphone, transcribe speech to text using built-in speech recognition, and return the transcription. The AI automatically decides which language to respond in.",
    )

    val screenshotToolInfo = ToolInfo(
        id = "screenshot",
        name = "Take Screenshot",
        description = "Capture the current device screen and save it for AI analysis. On Android 12+, requires MediaProjection permission (granted per-session when tool runs).",
    )

    val launchActivityToolInfo = ToolInfo(
        id = "launch_activity",
        name = "Launch Activity",
        description = "Launch any Android activity by package name and activity class name.",
    )

    val modifySettingsToolInfo = ToolInfo(
        id = "modify_settings",
        name = "Modify Settings",
        description = "Modify Android system/global/secure settings. Requires WRITE_SETTINGS permission (opens settings screen when tool runs if not granted).",
    )

    val readScreenTextToolInfo = ToolInfo(
        id = "read_screen_text",
        name = "Read Screen Text",
        description = "Read all visible text on the current screen using AccessibilityService. Requires Kai Screen Reader enabled in Accessibility settings (opens settings when tool runs if not enabled).",
    )

    val navigateScreenToolInfo = ToolInfo(
        id = "navigate_screen",
        name = "Navigate Screen",
        description = "Navigate on screen using AccessibilityService (click, scroll, back, home). Requires Kai Screen Reader enabled in Accessibility settings (opens settings when tool runs if not enabled).",
    )

    val phoneToolDefinitions = listOf(
        gpsLocationToolInfo,
        setGpsLocationToolInfo,
        takePictureToolInfo,
        hearSurroundingsToolInfo,
        screenshotToolInfo,
        launchActivityToolInfo,
        modifySettingsToolInfo,
        readScreenTextToolInfo,
        navigateScreenToolInfo,
        readContactsToolInfo,
        deviceInfoToolInfo,
        batteryInfoToolInfo,
        networkInfoToolInfo,
        wifiInfoToolInfo,
        clipboardToolInfo,
        installedAppsToolInfo,
        readCalendarToolInfo,
        writeContactToolInfo,
        getPhoneStateToolInfo,
        scanBluetoothToolInfo,
        listMediaToolInfo,
        readLogsToolInfo,
    )
}
