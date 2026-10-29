package com.hfspaces.explorer.testing

import com.hfspaces.explorer.data.security.TokenCipher

/** Reversible stand-in for the Keystore cipher, which Robolectric doesn't provide. */
class FakeTokenCipher : TokenCipher {
    override fun encrypt(plain: String) = TokenCipher.PREFIX + plain.reversed()

    override fun decrypt(stored: String): String? =
        if (isEncrypted(stored)) stored.removePrefix(TokenCipher.PREFIX).reversed() else stored
}
