package com.vela.data.security

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import okio.ByteString.Companion.encodeUtf8
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class SecureSessionStore {

    fun getToken(serverId: String?): String? {
        if (serverId.isNullOrBlank()) return null
        return keychainGet(tokenKey(serverId))
    }

    fun putToken(serverId: String, accessToken: String) {
        if (serverId.isBlank() || accessToken.isBlank()) return
        keychainSet(tokenKey(serverId), accessToken)
    }

    fun removeToken(serverId: String?) {
        if (serverId.isNullOrBlank()) return
        keychainDelete(tokenKey(serverId))
    }

    fun hasToken(serverId: String?): Boolean = !getToken(serverId).isNullOrBlank()

    private fun tokenKey(serverId: String): String = "token_${sha256(serverId)}"

    private fun keychainGet(account: String): String? = memScoped {
        val query = baseQuery(account)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)

        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)

        if (status == errSecSuccess) {
            val data = CFBridgingRelease(result.value) as? NSData
            data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
        } else {
            null
        }
    }

    private fun keychainSet(account: String, value: String) {
        keychainDelete(account)

        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val query = baseQuery(account)
        CFDictionaryAddValue(query, kSecValueData, CFBridgingRetain(data))
        CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)

        SecItemAdd(query, null)
        CFRelease(query)
    }

    private fun keychainDelete(account: String) {
        val query = baseQuery(account)
        SecItemDelete(query)
        CFRelease(query)
    }

    private fun baseQuery(account: String): CFMutableDictionaryRef {
        val query = CFDictionaryCreateMutable(null, 5, null, null)
            ?: throw IllegalStateException("Failed to create CFMutableDictionary")

        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, CFBridgingRetain(SERVICE_NAME))
        CFDictionaryAddValue(query, kSecAttrAccount, CFBridgingRetain(account))
        return query
    }

    private fun sha256(value: String): String {
        return value.encodeUtf8().sha256().hex()
    }

    companion object {
        private const val SERVICE_NAME = "com.vela.app.secure_auth_store"
    }
}