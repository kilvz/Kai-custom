package com.kai.custom

actual fun createSshConnectionManager(): SshConnectionManager {
    error("SSH is not available on WasmJS")
}
