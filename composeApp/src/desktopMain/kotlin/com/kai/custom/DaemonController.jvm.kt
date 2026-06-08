package com.kai.custom

import java.awt.AWTException
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities

actual fun createDaemonController(): DaemonController = SystemTrayDaemonController()

class SystemTrayDaemonController : DaemonController {
    private var trayIcon: TrayIcon? = null
    private var hiddenFrames = mutableListOf<java.awt.Window>()

    override fun start() {
        if (!SystemTray.isSupported()) return
        if (trayIcon != null) return
        try {
            SwingUtilities.invokeLater {
                val tray = SystemTray.getSystemTray()
                val image = java.awt.Toolkit.getDefaultToolkit().getImage(
                    java.net.URI("https://raw.githubusercontent.com/kilvz/Kai-custom/main/sandbox/icon.png").toURL(),
                ).getScaledInstance(16, 16, Image.SCALE_SMOOTH)

                val popup = PopupMenu()
                val showItem = MenuItem("Show Kai")
                showItem.addActionListener {
                    hiddenFrames.forEach { it.isVisible = true }
                    hiddenFrames.clear()
                }
                popup.add(showItem)

                val quitItem = MenuItem("Quit")
                quitItem.addActionListener { System.exit(0) }
                popup.add(quitItem)

                val icon = TrayIcon(image, "Kai", popup)
                icon.isImageAutoSize = true
                icon.addActionListener {
                    hiddenFrames.forEach { it.isVisible = true }
                    hiddenFrames.clear()
                }
                tray.add(icon)
                trayIcon = icon
            }
        } catch (_: AWTException) {
        } catch (_: Exception) {
        }
    }

    override fun stop() {
        try {
            SwingUtilities.invokeAndWait {
                trayIcon?.let { icon ->
                    try {
                        SystemTray.getSystemTray().remove(icon)
                    } catch (_: Exception) {}
                    trayIcon = null
                }
            }
        } catch (_: Exception) {
            trayIcon = null
        }
    }

    override fun startFloatingBall() = true

    override fun stopFloatingBall() {}

    fun hideWindow(frame: java.awt.Window) {
        hiddenFrames.add(frame)
        frame.isVisible = false
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                hiddenFrames.remove(frame)
                frame.isVisible = false
            }
        })
    }
}
