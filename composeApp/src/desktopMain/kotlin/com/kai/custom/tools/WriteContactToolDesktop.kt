package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema
import java.io.File

object WriteContactToolDesktop : Tool {
    override val schema = ToolSchema(
        name = "write_contact",
        description = "Create or update a contact in the system address book",
        parameters = mapOf(
            "name" to ParameterSchema(type = "string", description = "Full name of the contact", required = true),
            "phone" to ParameterSchema(type = "string", description = "Phone number", required = false),
            "email" to ParameterSchema(type = "string", description = "Email address", required = false),
        ),
    )

    val toolInfo = PhoneTools.writeContactToolInfo

    override suspend fun execute(args: Map<String, Any>): Any {
        val name = args["name"] as? String ?: return mapOf("success" to false, "error" to "name is required")
        val phone = args["phone"] as? String ?: ""
        val email = args["email"] as? String ?: ""
        val os = System.getProperty("os.name").lowercase()

        return try {
            when {
                os.contains("linux") -> {
                    val vcfContent = buildVcf(name, phone, email)
                    val vcfDir = File(System.getProperty("user.home"), "Contacts")
                    vcfDir.mkdirs()
                    val vcfFile = File(vcfDir, "${name.replace(" ", "_")}.vcf")
                    vcfFile.writeText(vcfContent)
                    mapOf("success" to true, "message" to "Contact saved to ${vcfFile.absolutePath}")
                }

                else -> {
                    val vcfContent = buildVcf(name, phone, email)
                    val home = System.getProperty("user.home")
                    val vcfFile = File(home, "Desktop/${name.replace(" ", "_")}.vcf")
                    vcfFile.writeText(vcfContent)
                    mapOf("success" to true, "message" to "Contact exported to ${vcfFile.absolutePath}")
                }
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to write contact: ${e.message}")
        }
    }

    private fun buildVcf(name: String, phone: String, email: String): String = buildString {
        appendLine("BEGIN:VCARD")
        appendLine("VERSION:3.0")
        appendLine("FN:$name")
        appendLine("N:${name.split(" ").lastOrNull() ?: ""};${name.split(" ").firstOrNull() ?: ""};;;")
        if (phone.isNotBlank()) appendLine("TEL;TYPE=CELL:$phone")
        if (email.isNotBlank()) appendLine("EMAIL:$email")
        appendLine("END:VCARD")
    }
}
