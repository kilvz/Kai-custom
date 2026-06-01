package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
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

    val phoneToolDefinitions = listOf(
        gpsLocationToolInfo,
        readContactsToolInfo,
        deviceInfoToolInfo,
        batteryInfoToolInfo,
        networkInfoToolInfo,
        wifiInfoToolInfo,
        clipboardToolInfo,
        installedAppsToolInfo,
    )
}
