package com.kai.custom.tools

import com.kai.custom.SshAuthMethod
import com.kai.custom.SshConfig
import com.kai.custom.SshConnectionManager
import com.kai.custom.SshProfile
import com.kai.custom.data.AppSettings
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import org.koin.java.KoinJavaComponent.inject

private const val TOOL_DESCRIPTION = """Connect to an SSH server with the specified credentials.

After connecting, the AI can use ssh_execute_command to run commands on the server. The connection stays alive until explicitly disconnected.

Parameters:
- host (required): SSH server hostname or IP
- port (optional, default 22): SSH server port
- username (required): SSH username
- auth_method (optional, default "password"): Authentication method — "password" or "key"
- password: SSH password (required if auth_method is "password")
- private_key: SSH private key content in PEM format (required if auth_method is "key")
- profile_name (optional): Save these credentials as a named profile for later reuse

If profile_name is provided, the credentials are saved and can be used again by calling ssh_connect with only the profile_name.

Example:
  Connect and save profile:
  {"host": "192.168.1.100", "port": 22, "username": "root", "auth_method": "password", "password": "mypassword", "profile_name": "my-server"}
  
  Reconnect using saved profile:
  {"profile_name": "my-server"}

  Connect with private key:
  {"host": "myserver.com", "port": 2222, "username": "admin", "auth_method": "key", "private_key": "-----BEGIN RSA PRIVATE KEY-----\n...", "profile_name": "prod"}"""

object SshConnectTool : Tool {
    private val sshManager: SshConnectionManager by inject(SshConnectionManager::class.java)
    private val appSettings: AppSettings by inject(AppSettings::class.java)

    override val schema = ToolSchema(
        name = "ssh_connect",
        description = TOOL_DESCRIPTION,
        parameters = mapOf(
            "host" to ParameterSchema("string", "SSH server hostname or IP address", false),
            "port" to ParameterSchema("integer", "SSH server port (default 22)", false),
            "username" to ParameterSchema("string", "SSH username", false),
            "auth_method" to ParameterSchema("string", "Authentication method: 'password' or 'key' (default 'password')", false),
            "password" to ParameterSchema("string", "SSH password (required if auth_method is 'password')", false),
            "private_key" to ParameterSchema("string", "SSH private key in PEM format (required if auth_method is 'key')", false),
            "profile_name" to ParameterSchema("string", "Use or save a named SSH profile. If host is also provided, saves the credentials under this name and connects. If only profile_name is given, loads that profile and connects.", false),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val profileName = args["profile_name"] as? String

        val host = args["host"] as? String
        val username = args["username"] as? String

        val config: SshConfig
        val actualProfileName: String?

        if (host == null && username == null && profileName != null) {
            val profile = appSettings.getSshProfiles().find { it.name == profileName }
                ?: return mapOf("success" to false as Any, "error" to "Profile '$profileName' not found. Available profiles: ${appSettings.getSshProfiles().joinToString { p -> "'${p.name}'" }}" as Any)
            config = SshConfig(
                host = profile.host,
                port = profile.port,
                username = profile.username,
                authMethod = profile.authMethod,
                password = profile.password,
                privateKey = profile.privateKey,
            )
            actualProfileName = profileName
        } else {
            if (host == null) return mapOf("success" to false as Any, "error" to "Host is required (or provide a profile_name to use a saved profile)" as Any)
            if (username == null) return mapOf("success" to false as Any, "error" to "Username is required" as Any)

            val port = (args["port"] as? Number)?.toInt() ?: 22
            val authMethodStr = (args["auth_method"] as? String)?.lowercase() ?: "password"
            val authMethod = if (authMethodStr == "key") SshAuthMethod.KEY else SshAuthMethod.PASSWORD
            val password = args["password"] as? String ?: ""
            val privateKey = args["private_key"] as? String ?: ""

            if (authMethod == SshAuthMethod.PASSWORD && password.isBlank()) {
                return mapOf("success" to false as Any, "error" to "Password is required when auth_method is 'password'" as Any)
            }
            if (authMethod == SshAuthMethod.KEY && privateKey.isBlank()) {
                return mapOf("success" to false as Any, "error" to "Private key is required when auth_method is 'key'" as Any)
            }

            config = SshConfig(
                host = host,
                port = port,
                username = username,
                authMethod = authMethod,
                password = password,
                privateKey = privateKey,
            )
            actualProfileName = profileName
        }

        if (actualProfileName != null) {
            val profile = SshProfile(
                name = actualProfileName,
                host = config.host,
                port = config.port,
                username = config.username,
                authMethod = config.authMethod,
                password = config.password,
                privateKey = config.privateKey,
            )
            appSettings.saveSshProfile(profile)
            appSettings.setActiveSshProfileName(actualProfileName)
        }

        val result = sshManager.connect(config)
        return if (result.isSuccess) {
            val connMsg = "Connected to ${config.username}@${config.host}:${config.port}"
            val profileMsg = if (actualProfileName != null) " and saved as profile '$actualProfileName'" else ""
            val availableProfiles = appSettings.getSshProfiles()
            val profilesList = if (availableProfiles.isNotEmpty()) {
                " Available profiles: ${availableProfiles.joinToString { p -> "'${p.name}' (${p.username}@${p.host}:${p.port})" }}"
            } else {
                ""
            }
            mapOf("success" to true as Any, "message" to "$connMsg$profileMsg.$profilesList" as Any)
        } else {
            mapOf("success" to false as Any, "error" to (result.exceptionOrNull()?.message ?: "Connection failed") as Any)
        }
    }

    val toolInfo = ToolInfo(
        id = "ssh_connect",
        name = "Connect SSH",
        description = "Connect to an SSH server with specified credentials. Can save and reuse named profiles.",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )
}
